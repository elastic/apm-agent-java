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
package co.elastic.apm.agent.opentelemetry.metrics;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.opentelemetry.OtelTestUtils;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.ContextPropagators;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class GlobalUserMetricsSdkTest extends AbstractInstrumentationTest {

    @Test
    public void checkUserConfiguredGlobalMetricsSDKPreserved() {
        OtelTestUtils.resetElasticOpenTelemetry();
        OtelTestUtils.clearGlobalOpenTelemetry();

        MeterProvider userSdk = Mockito.mock(MeterProvider.class);
        GlobalOpenTelemetry.set(new OpenTelemetry() {
            @Override
            public TracerProvider getTracerProvider() {
                return TracerProvider.noop();
            }

            @Override
            public ContextPropagators getPropagators() {
                return ContextPropagators.noop();
            }

            @Override
            public MeterProvider getMeterProvider() {
                return userSdk;
            }
        });

        OpenTelemetry globalOtel = GlobalOpenTelemetry.get();

        assertThat(globalOtel.getMeterProvider()).isSameAs(userSdk);
        assertThat(globalOtel.getTracerProvider().getClass().getName()).startsWith("co.elastic");

    }
}
