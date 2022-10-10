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
package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class LegacyApacheHttpClientVersionIT {

    private final TestClassWithDependencyRunner runner;


    public LegacyApacheHttpClientVersionIT(List<String> dependencies) throws Exception {
        this.runner = new TestClassWithDependencyRunner(dependencies, LegacyApacheHttpClientBasicHttpRequestInstrumentationTest.class);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {List.of("org.apache.httpcomponents:httpclient:4.0")},
            {List.of("org.apache.httpcomponents:httpclient:4.0.1")},
            {List.of("org.apache.httpcomponents:httpclient:4.1.3")},
            {List.of("org.apache.httpcomponents:httpclient:4.2.5")}
        });
    }

    @Test
    public void test() {
        runner.run();
    }
}
