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
package co.elastic.apm.agent.opentelemetry;

import co.elastic.apm.agent.testutils.TestClassWithDependencyRunner;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

public class OpenTelemetryVersionIT {

    @ParameterizedTest
    @ValueSource(strings = {
        "1.0.1",
        "1.1.0",
        "1.2.0",
        "1.3.0",
        "1.4.1",
        "1.5.0",
        "1.6.0",
        "1.7.1",
        "1.9.0",
        "1.10.1",
        "1.11.0",
        "1.12.0",
        "1.13.0",
        "1.14.0",
        "1.15.0",
        "1.16.0",
        "1.17.0",
        "1.18.0",
        "1.19.0",
        "1.20.0",
        "1.21.0"
    })
    void testTracingVersion(String version) throws Exception {
        List<String> dependencies = List.of(
            "io.opentelemetry:opentelemetry-api:" + version,
            "io.opentelemetry:opentelemetry-context:" + version,
            "io.opentelemetry:opentelemetry-semconv:" + version + "-alpha");
        TestClassWithDependencyRunner runner = new TestClassWithDependencyRunner(dependencies,
            "co.elastic.apm.agent.opentelemetry.tracing.ElasticOpenTelemetryTest",
            "co.elastic.apm.agent.opentelemetry.tracing.AbstractOpenTelemetryTest",
            "co.elastic.apm.agent.opentelemetry.tracing.ElasticOpenTelemetryTest$MapGetter");
        runner.run();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "1.10.0",
        //"1.11.0",
        //"1.12.0",
        //"1.13.0",
        "1.14.0",
        "1.15.0",
        //"1.16.0",
        //"1.17.0",
        //"1.18.0",
        //"1.19.0",
        //"1.20.0",
        "1.21.0"
    })
    void testAgentProvidedMetricsSdkForApiVersion(String version) throws Exception {
        List<String> dependencies = List.of(
            "io.opentelemetry:opentelemetry-api:" + version,
            "io.opentelemetry:opentelemetry-context:" + version,
            "io.opentelemetry:opentelemetry-semconv:" + version + "-alpha");
        TestClassWithDependencyRunner runner = new TestClassWithDependencyRunner(dependencies,
            "co.elastic.apm.agent.opentelemetry.metrics.AgentProvidedSdkOtelMetricsTest",
            "co.elastic.apm.agent.otelmetricsdk.AbstractOtelMetricsTest",
            "co.elastic.apm.agent.otelmetricsdk.AbstractOtelMetricsTest$1",
            "co.elastic.apm.agent.opentelemetry.OtelTestUtils");
        runner.run();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "1.16.0",
        //"1.17.0",
        //"1.18.0",
        //"1.19.0",
        //"1.20.0",
        "1.21.0"
    })
    void testUserProvidedMetricsSdkVersion(String version) throws Exception {
        List<String> dependencies = List.of(
            "io.opentelemetry:opentelemetry-api:" + version,
            "io.opentelemetry:opentelemetry-sdk-metrics:" + version,
            "io.opentelemetry:opentelemetry-sdk-common:" + version,
            "io.opentelemetry:opentelemetry-context:" + version,
            "io.opentelemetry:opentelemetry-semconv:" + version + "-alpha");
        TestClassWithDependencyRunner runner = new TestClassWithDependencyRunner(dependencies,
            "co.elastic.apm.agent.otelmetricsdk.PrivateUserSdkOtelMetricsTest",
            "co.elastic.apm.agent.otelmetricsdk.AbstractOtelMetricsTest",
            "co.elastic.apm.agent.otelmetricsdk.AbstractOtelMetricsTest$1",
            "co.elastic.apm.agent.otelmetricsdk.DummyMetricReader");
        runner.run();
    }

    @ParameterizedTest
    @CsvSource(value = {
        "1.10.0|1.10.0-alpha",
        "1.11.0|1.11.0-alpha",
        "1.12.0|1.12.0-alpha",
        "1.13.0|1.13.0-alpha",
        "1.14.0|1.14.0",
        "1.15.0|1.15.0",
    }, delimiterString = "|")
    void testUnsupportedMetricsSdkVersionsIgnored(String apiVersion, String sdkVersion) throws Exception {
        List<String> dependencies = List.of(
            "io.opentelemetry:opentelemetry-api:" + apiVersion,
            "io.opentelemetry:opentelemetry-sdk-metrics:" + sdkVersion,
            "io.opentelemetry:opentelemetry-sdk-common:" + apiVersion,
            "io.opentelemetry:opentelemetry-context:" + apiVersion,
            "io.opentelemetry:opentelemetry-semconv:" + apiVersion + "-alpha");


        TestClassWithDependencyRunner runner = new TestClassWithDependencyRunner(dependencies,
            "co.elastic.apm.agent.otelmetricsdk.UnsupportedSdkVersionIgnoredTest",
            "co.elastic.apm.agent.otelmetricsdk.DummyMetricReader");
        runner.run();
    }

}
