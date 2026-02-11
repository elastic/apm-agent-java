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

import co.elastic.apm.agent.restclient.SpringRestClientInstrumentationTest;
import co.elastic.apm.agent.testutils.TestClassWithDependencyRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@EnabledForJreRange(min = JRE.JAVA_17, disabledReason = "Spring 6 and up require JDK 17")
public class SpringRestClientVersionsIT {


    @Test
    void version6() throws Exception {
        testVersionImpl("6.1.0");
    }

    @Test
    void version7() throws Exception {
        testVersionImpl("7.0.0");
    }

    void testVersionImpl(String version) throws Exception {
        var springDependencies = Stream.of(
                "spring-webmvc",
                "spring-aop",
                "spring-beans",
                "spring-context",
                "spring-core",
                "spring-expression",
                "spring-web")
            .map(s -> String.format("org.springframework:%s:%s", s, version));

        var additionalDependencies = Stream.of(
            "io.micrometer:micrometer-observation:1.10.2",
            "io.micrometer:micrometer-commons:1.10.2",
            "commons-logging:commons-logging:1.3.0",
            "org.apache.logging.log4j:log4j-api:2.25.3"
        );

        List<String> dependencies = Stream.concat(springDependencies, additionalDependencies).collect(Collectors.toList());

        new TestClassWithDependencyRunner(dependencies, SpringRestClientInstrumentationTest.class.getName(), SpringRestClientInstrumentationTest.Java17Code.class.getName()).run();
    }
}
