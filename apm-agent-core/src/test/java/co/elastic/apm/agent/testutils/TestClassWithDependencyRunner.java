/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.testutils;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.extension.ConditionEvaluationResult.disabled;
import static org.junit.jupiter.api.extension.ConditionEvaluationResult.enabled;
import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

public class TestClassWithDependencyRunner extends AbstractTestClassWithDependencyRunner {


    /**
     * Prevents test from running when the test class is not executed from within a {@link TestClassWithDependencyRunner}.
     * <p>
     * This if for example useful when attempting to test that an instrumentation is *not* active for unsupported versions
     * of the instrumentation target. To test this, one would create a test that checks that the instrumentation is not active
     * and run it via the {@link TestClassWithDependencyRunner} for the unsupported versions of the instrumentation target.
     * <p>
     * However, the test itself would also be run by maven outside the {@link TestClassWithDependencyRunner}, because it is a
     * normal unit test. In this environment the test is executed with the latest version of the instrumentation target (from the pom.xml),
     * which in turn would cause the test to fail because this version is actually supported.
     * To prevent these "wrong" failures, this annotation can be used to disable the test outside the {@link TestClassWithDependencyRunner}.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ExtendWith(DisableOutsideOfRunnerCondition.class)
    public @interface DisableOutsideOfRunner {
        /**
         * @return a custom reason for disabling this outside of the dependency runner
         */
        String value() default "";
    }

    public static class DisableOutsideOfRunnerCondition implements ExecutionCondition {

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            AnnotatedElement element = context.getElement().orElse(null);
            return findAnnotation(element, DisableOutsideOfRunner.class)
                .map(annotation -> disabled(element + " is @DisableOutsideDependencyRunner", annotation.value()))
                .orElse(enabled("@DisableOutsideDependencyRunner is not present"));
        }
    }

    public TestClassWithDependencyRunner(List<String> dependencies, String testClass, String... classesReferencingDependency) throws Exception {
        super(dependencies, testClass, classesReferencingDependency);
    }

    public void run() {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(selectClass(testClass))
            .configurationParameter("junit.jupiter.conditions.deactivate", DisableOutsideOfRunnerCondition.class.getName())
            .build();
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        for (TestExecutionSummary.Failure failure : listener.getSummary().getFailures()) {
            System.out.println(failure);
            failure.getException().printStackTrace();
        }
        assertThat(listener.getSummary().getTestsFailedCount())
            .describedAs("at least one test failure reported, see stack trace for investigation")
            .isZero();
    }

}
