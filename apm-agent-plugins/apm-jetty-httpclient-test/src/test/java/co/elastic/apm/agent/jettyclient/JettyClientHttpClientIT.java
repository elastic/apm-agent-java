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
package co.elastic.apm.agent.jettyclient;

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class JettyClientHttpClientIT {

    private final TestClassWithDependencyRunner runner;
    private final TestClassWithDependencyRunner asyncTestRunner;

    public JettyClientHttpClientIT(List<String> dependencies) throws Exception {
        this.runner = new TestClassWithDependencyRunner(dependencies, JettyClientSyncInstrumentationTest.class);
        this.asyncTestRunner = new TestClassWithDependencyRunner(dependencies, JettyClientASyncInstrumentationTest.class);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {List.of("org.eclipse.jetty:jetty-client:11.0.6", "org.eclipse.jetty:jetty-util:11.0.6", "org.eclipse.jetty:jetty-io:11.0.6", "org.eclipse.jetty:jetty-http:11.0.6")},
            {List.of("org.eclipse.jetty:jetty-client:10.0.6", "org.eclipse.jetty:jetty-util:11.0.6", "org.eclipse.jetty:jetty-io:11.0.6", "org.eclipse.jetty:jetty-http:11.0.6")},
            {List.of("org.eclipse.jetty:jetty-client:9.4.43.v20210629", "org.eclipse.jetty:jetty-util:9.4.43.v20210629", "org.eclipse.jetty:jetty-io:9.4.43.v20210629", "org.eclipse.jetty:jetty-http:9.4.43.v20210629")},
            {List.of("org.eclipse.jetty:jetty-client:9.3.29.v20201019", "org.eclipse.jetty:jetty-util:9.3.29.v20201019", "org.eclipse.jetty:jetty-io:9.3.29.v20201019", "org.eclipse.jetty:jetty-http:9.3.29.v20201019")},
            {List.of("org.eclipse.jetty:jetty-client:9.2.16.v20160414", "org.eclipse.jetty:jetty-util:9.2.16.v20160414", "org.eclipse.jetty:jetty-io:9.2.16.v20160414", "org.eclipse.jetty:jetty-http:9.2.16.v20160414")},
            {List.of("org.eclipse.jetty:jetty-client:9.1.6.v20160112", "org.eclipse.jetty:jetty-util:9.1.6.v20160112", "org.eclipse.jetty:jetty-io:9.1.6.v20160112", "org.eclipse.jetty:jetty-http:9.1.6.v20160112")},
            {List.of("org.eclipse.jetty:jetty-client:9.1.0.v20131115","org.eclipse.jetty:jetty-util:9.1.0.v20131115", "org.eclipse.jetty:jetty-io:9.1.0.v20131115", "org.eclipse.jetty:jetty-http:9.1.0.v20131115")},
       });
    }

    @Test
    public void testSync() {
        runner.run();
    }

    @Test
    public void testAsync() {
        asyncTestRunner.run();
    }
}
