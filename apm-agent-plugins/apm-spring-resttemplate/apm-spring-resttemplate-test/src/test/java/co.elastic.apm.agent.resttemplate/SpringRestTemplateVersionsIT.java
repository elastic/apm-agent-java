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
package co.elastic.apm.agent.resttemplate;

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SpringRestTemplateVersionsIT {

    @ParameterizedTest
    @CsvSource({
        "3.0.3.RELEASE", // lower versions are not supported
        "3.1.0.RELEASE",
        "3.2.0.RELEASE",
        "4.0.0.RELEASE",
        "4.1.0.RELEASE",
        "4.2.0.RELEASE",
        "4.3.0.RELEASE",
        "5.0.0.RELEASE",
        "5.1.0.RELEASE",
        "5.2.0.RELEASE",
        "5.3.0"})
    void testVersion(String version) throws Exception {
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

        if (version.startsWith("5.")) {
            dependencies.add(String.format("org.springframework:spring-jcl:%s", version));
        } else {
            dependencies.add("org.slf4j:jcl-over-slf4j:1.7.30");
        }

        TestClassWithDependencyRunner runner = new TestClassWithDependencyRunner(dependencies, SprintRestTemplateIntegration.class);
        runner.run();
    }


}
