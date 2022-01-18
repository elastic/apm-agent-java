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
package co.elastic.apm.agent.lettuce;

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class Lettuce4VersionsIT {

    private final TestClassWithDependencyRunner runner;

    public Lettuce4VersionsIT(List<String> dependencies) throws Exception {
        System.setProperty("io.lettuce.core.kqueue", "false");
        runner = new TestClassWithDependencyRunner(dependencies, Lettuce4InstrumentationIT.class);
    }

    @Parameterized.Parameters(name= "{0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { List.of("biz.paluch.redis:lettuce:4.5.0.Final", "io.netty:netty-all:4.1.29.Final") },
            { List.of("biz.paluch.redis:lettuce:4.4.6.Final", "io.netty:netty-all:4.1.24.Final") },
            { List.of("biz.paluch.redis:lettuce:4.3.3.Final", "io.netty:netty-all:4.1.13.Final") },
            { List.of("biz.paluch.redis:lettuce:4.2.2.Final", "io.netty:netty-all:4.0.40.Final") },
            { List.of("biz.paluch.redis:lettuce:4.1.2.Final", "io.netty:netty-all:4.0.34.Final", "org.latencyutils:LatencyUtils:2.0.3") },
            { List.of("biz.paluch.redis:lettuce:4.0.2.Final", "io.netty:netty-all:4.0.30.Final") },
        });
    }

    @Test
    public void testLettuce() {
        runner.run();
    }
}
