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
package co.elastic.apm.agent.embeddedotel;

import co.elastic.apm.agent.embeddedotel.proxy.ProxyMeter;
import co.elastic.apm.agent.tracer.Tracer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EmbeddedSdkManagerTest {

    /**
     * The instrumentation of the agent is performed before {@link EmbeddedSdkManager#init(Tracer)} is invoked.
     * This means if the agent is started asynchronously, it can happen that {@link EmbeddedSdkManager#getMeterProvider()}
     * is invoked before the tracer has been provided.
     * This test verifies that in that case no exception occurs and a noop-meter implementation is used.
     */
    @Test
    public void ensureNoExceptionOnMissingTracer() throws Exception {
        ProxyMeter meter = new EmbeddedSdkManager().getMeterProvider().get("foobar");
        assertThat(meter.getDelegate()).isInstanceOf(Class.forName("io.opentelemetry.api.metrics.DefaultMeter"));
    }
}
