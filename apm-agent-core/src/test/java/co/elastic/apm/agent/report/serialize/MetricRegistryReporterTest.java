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
package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.configuration.ServiceInfo;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.source.SimpleSource;

import static org.assertj.core.api.Assertions.assertThat;

class MetricRegistryReporterTest {

    @Test
    void testReportedMetricsUseDefaultServiceNameIfServiceNameIsExplicitlySet() throws Exception {
        ElasticApmTracer tracer = null;
        try {
            MockReporter reporter = new MockReporter();
            tracer = new ElasticApmTracerBuilder()
                .configurationRegistry(SpyConfiguration.createSpyConfig(SimpleSource.forTest("service_name", "foo")))
                .reporter(reporter)
                .buildAndStart();
            tracer.setServiceInfoForClassLoader(MetricRegistryReporterTest.class.getClassLoader(), ServiceInfo.of("MetricRegistryReporterTest"));

            new MetricRegistryReporter(tracer).run();

            assertThat(reporter.getBytes()).isNotEmpty();

            ObjectMapper objectMapper = new ObjectMapper();
            for (byte[] json : reporter.getBytes()) {
                JsonNode jsonNode = objectMapper.readTree(json);
                assertThat(jsonNode.get("metricset").get("service")).isNull();
            }
        } finally {
            tracer.stop();
        }
    }
}
