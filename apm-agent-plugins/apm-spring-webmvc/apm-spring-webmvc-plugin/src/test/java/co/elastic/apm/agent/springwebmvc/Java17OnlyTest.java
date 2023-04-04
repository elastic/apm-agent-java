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
package co.elastic.apm.agent.springwebmvc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Why not just put @EnabledForJreRange directly on the test classes?
 * JUnit reflectively loads the target class when discovering tests.
 * Because of spring, the tests contain references/annotations compiled with Java 17.
 * This in turn leads to UnsupportedClassVersionErrors before JUnit can evaluate the @EnableForJRERange when running on older java versions (e.g. 11).
 * <p>
 * Therefore, this class can be used to wrap tests, as it programmatically triggers the test execution.
 * The actual test implementation should not be named *Test to not be discovered by the maven surefire plugin.
 */
public abstract class Java17OnlyTest {

    private Class<?> actualTestClass;

    public Java17OnlyTest(Class<?> testClazz) {
        this.actualTestClass = testClazz;
    }

    @EnabledForJreRange(min = JRE.JAVA_17)
    @Test
    public void runTests() {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(selectClass(actualTestClass))
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
