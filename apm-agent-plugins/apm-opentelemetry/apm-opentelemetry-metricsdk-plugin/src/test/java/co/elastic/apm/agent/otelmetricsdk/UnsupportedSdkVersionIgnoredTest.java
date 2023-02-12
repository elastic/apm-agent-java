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
package co.elastic.apm.agent.otelmetricsdk;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.testutils.TestClassWithDependencyRunner;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Only executed via integration tests (OpenTelemetryVersionIT).
 */
public class UnsupportedSdkVersionIgnoredTest extends AbstractInstrumentationTest {

    @Test
    @TestClassWithDependencyRunner.DisableOutsideOfRunner
    void checkUnsupportedVersionIgnored() {
        SdkMeterProvider meterProvider = SdkMeterProvider.builder().build();
        Meter testMeter = meterProvider.get("test");
        LongCounter counter = testMeter.counterBuilder("counter").build();

        counter.add(42);

        meterProvider.forceFlush();
        assertThat(reporter.getBytes()).isEmpty();
    }
}
