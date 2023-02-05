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

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runners.BlockJUnit4ClassRunner;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @deprecated use {@link TestClassWithDependencyRunner} and test with junit5 instead
 */
@Deprecated
public class JUnit4TestClassWithDependencyRunner extends AbstractTestClassWithDependencyRunner {

    @Nullable
    private final BlockJUnit4ClassRunner testRunner;

    public JUnit4TestClassWithDependencyRunner(String groupId, String artifactId, String version, Class<?> testClass, Class<?>... classesReferencingDependency) throws Exception {
        this(Collections.singletonList(groupId + ":" + artifactId + ":" + version), testClass, classesReferencingDependency);
    }

    public JUnit4TestClassWithDependencyRunner(List<String> dependencies, Class<?> testClass, Class<?>... classesReferencingDependency) throws Exception {
        this(dependencies, testClass.getName(), Arrays.stream(classesReferencingDependency).map(Class::getName).toArray(String[]::new));
    }

    public JUnit4TestClassWithDependencyRunner(List<String> dependencies, String testClass, String... classesReferencingDependency) throws Exception {
        super(dependencies, testClass, classesReferencingDependency);
        testRunner = new BlockJUnit4ClassRunner(this.testClass);
    }

    public void run() {
        if (testRunner == null) {
            throw new IllegalStateException();
        }
        Result result = new JUnitCore().run(testRunner);
        for (Failure failure : result.getFailures()) {
            System.out.println(failure);
            failure.getException().printStackTrace();
        }
        assertThat(result.wasSuccessful()).isTrue();
    }

}
