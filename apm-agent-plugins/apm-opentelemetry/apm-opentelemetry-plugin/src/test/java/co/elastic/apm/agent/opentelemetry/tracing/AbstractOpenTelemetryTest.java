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
package co.elastic.apm.agent.opentelemetry.tracing;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.junit.Before;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractOpenTelemetryTest extends AbstractInstrumentationTest {

    protected OpenTelemetry openTelemetry;
    protected Tracer otelTracer;

    @Before
    public void setUp() {
        this.openTelemetry = GlobalOpenTelemetry.get();
        assertThat(openTelemetry).isSameAs(GlobalOpenTelemetry.get());
        otelTracer = openTelemetry.getTracer(null);

        // otel spans are not recycled for now
        disableRecyclingValidation();

        // otel spans should have unknown outcome by default unless explicitly set through API
        reporter.disableCheckUnknownOutcome();
    }
}
