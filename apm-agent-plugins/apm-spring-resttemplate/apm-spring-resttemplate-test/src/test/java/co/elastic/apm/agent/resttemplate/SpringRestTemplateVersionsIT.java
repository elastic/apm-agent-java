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
package co.elastic.apm.agent.resttemplate;
/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */

import co.elastic.apm.agent.testutils.JUnit4TestClassWithDependencyRunner;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SpringRestTemplateVersionsIT {

    @ParameterizedTest
    @CsvSource(delimiterString = "|", value = {
        "3.1.1.RELEASE|true", // lower versions are not supported (3.1.1 is from 2012)
        "3.2.0.RELEASE|true",
        "4.0.0.RELEASE|true",
        "4.1.0.RELEASE|true",
        "4.2.0.RELEASE|true",
        "4.3.0.RELEASE|true",
    })
    void testVersion4AndOlder(String version, boolean isSupported) throws Exception {
        testVersionImpl(version, isSupported, List.of("org.slf4j:jcl-over-slf4j:1.7.30"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "5.0.0.RELEASE",
        "5.1.0.RELEASE",
        "5.2.0.RELEASE",
        "[5.3.0,6.0.0)", // using ivy range specifier to make test against later versions
    })
    void testVersion5(String version) throws Exception {
        testVersionImpl(version, true, List.of(String.format("org.springframework:spring-jcl:%s", version)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "6.0.5"
    })
    @EnabledForJreRange(min = JRE.JAVA_17, disabledReason = "Spring 6 requires JDK 17")
    void testVersion6(String version) throws Exception {
        testVersionImpl(version, true, List.of(
            String.format("org.springframework:spring-jcl:%s", version),
            "io.micrometer:micrometer-observation:1.10.2",
            "io.micrometer:micrometer-commons:1.10.2"
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "7.0.0"
    })
    @EnabledForJreRange(min = JRE.JAVA_17, disabledReason = "Spring 7 requires JDK 17")
    void testVersion7(String version) throws Exception {
        testVersionImpl(version, true, List.of(
            "commons-logging:commons-logging:1.3.0",
            "io.micrometer:micrometer-observation:1.10.2",
            "io.micrometer:micrometer-commons:1.10.2",
            "org.apache.logging.log4j:log4j-api:2.25.3"
        ));
    }

    void testVersionImpl(String version, boolean isSupported, List<String> additionalDependencies) throws Exception {
        List<String> dependencies = Stream.of(
                "spring-webmvc",
                "spring-aop",
                "spring-beans",
                "spring-context",
                "spring-core",
                "spring-expression",
                "spring-web")
            .map(s -> String.format("org.springframework:%s:%s", s, version))
            .collect(Collectors.toList());

        dependencies.addAll(additionalDependencies);

        Class<?> testClass = SprintRestTemplateIntegration.class;
        Class<?>[] otherClasses = new Class<?>[0];
        if (!isSupported) {
            otherClasses = new Class[]{SprintRestTemplateIntegration.class};
            testClass = SpringRestTemplateIntegrationNoOp.class;
        }
        JUnit4TestClassWithDependencyRunner runner = new JUnit4TestClassWithDependencyRunner(dependencies, testClass, otherClasses);
        runner.run();
    }

}
