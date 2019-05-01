/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.metrics;

import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.report.ReporterConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricRegistryTest {

    private MetricRegistry metricRegistry;
    private ReporterConfiguration config;

    @BeforeEach
    void setUp() {
        config = mock(ReporterConfiguration.class);
        metricRegistry = new MetricRegistry(config);
    }

    @Test
    void testDisabledMetrics() {
        when(config.getDisableMetrics()).thenReturn(List.of(WildcardMatcher.valueOf("jvm.gc.*")));
        final DoubleSupplier problematicMetric = () -> {
            throw new RuntimeException("Huston, we have a problem");
        };
        metricRegistry.addUnlessNegative("jvm.gc.count", emptyMap(), problematicMetric);
        metricRegistry.addUnlessNan("jvm.gc.count", emptyMap(), problematicMetric);
        metricRegistry.add("jvm.gc.count", emptyMap(), problematicMetric);
        assertThat(metricRegistry.getMetricSets()).isEmpty();
    }
}
