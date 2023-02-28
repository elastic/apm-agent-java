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

import co.elastic.apm.agent.embeddedotel.EmbeddedSdkTestUtil;
import co.elastic.apm.agent.opentelemetry.OtelTestUtils;
import co.elastic.apm.agent.otelmetricsdk.AbstractOtelMetricsTest;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.Method;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class AgentProvidedSdkOtelMetricsTest extends AbstractOtelMetricsTest {

    @BeforeEach
    public void cleanGlobalOtel() {
        OtelTestUtils.resetElasticOpenTelemetry();
        OtelTestUtils.clearGlobalOpenTelemetry();
        EmbeddedSdkTestUtil.stopAndReset(tracer);
    }

    @Override
    protected MeterProvider createOrLookupMeterProvider() {
        return GlobalOpenTelemetry.getMeterProvider();
    }

    @Override
    protected void invokeSdkForceFlush() {
        MeterProvider meterProvider = getMeterProvider();
        try {
            //the MeterProvider is currently wrapped by our bridge. We need to unwrap it first
            Method unwrapMethod = meterProvider.getClass().getMethod("unwrapBridge");
            Object proxyMeterProvider = unwrapMethod.invoke(meterProvider);

            //Now we have a ProxyMeterProvider, which we need to unwrap aswell
            Method getDelegateMethod = proxyMeterProvider.getClass().getMethod("getDelegate");
            Object unwrappedMeterProvider = getDelegateMethod.invoke(proxyMeterProvider);

            // Calls SdkMeterProvider.forceFlush()
            Method forceFlush = unwrappedMeterProvider.getClass().getMethod("forceFlush");
            forceFlush.invoke(unwrappedMeterProvider);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Meter createMeter(String name) {
        Meter meter = GlobalOpenTelemetry.getMeter(name);
        //make sure that we use a "hidden" SDK provided by the agent (and not the one visible to our classloader)
        assertThat(meter.getClass().getName()).startsWith("co.elastic.apm.");
        return meter;
    }

    //This test can be used to manually verify that otel-SDK logs are redirected to the agent logs
    //This needs to be executed via /.mvn package, because otherwise shading of the embedded-sdk didn't take place
    //@Test
    void generateLogs() {
        Meter meter = createMeter("blub");
        LongCounter foo = meter.counterBuilder("foo").build();

        for (int i = 0; i < 42; i++) {
            foo.add(-42); //will generate a warning because negative values are not allowed
        }
    }
}
