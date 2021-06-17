/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.opentelemetry;

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class OpenTelemetryVersionIT {
    private final TestClassWithDependencyRunner runner;

    public OpenTelemetryVersionIT(String version) throws Exception {
        List<String> dependencies = List.of(
            "io.opentelemetry:opentelemetry-api:" + version,
            "io.opentelemetry:opentelemetry-context:" + version,
            "io.opentelemetry:opentelemetry-semconv:1.0.0-alpha");
        runner = new TestClassWithDependencyRunner(dependencies,
            "co.elastic.apm.agent.opentelemetry.sdk.ElasticOpenTelemetryTest",
            "co.elastic.apm.agent.opentelemetry.sdk.ElasticOpenTelemetryTest$MapGetter");
    }

    @Parameterized.Parameters(name= "{0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"0.17.0"},
            {"0.17.1"},
            {"1.0.0"},
        });
    }

    @Test
    public void testVersions() throws Exception {
        runner.run();
    }
}
