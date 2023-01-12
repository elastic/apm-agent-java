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
package co.elastic.apm.agent.opentelemetry.metrics.bridge;

import co.elastic.apm.agent.opentelemetry.metrics.bridge.latest.BridgeBatchCallback;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.latest.BridgeDoubleCounterBuilder;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.latest.BridgeDoubleGaugeBuilder;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.latest.BridgeDoubleUpDownCounterBuilder;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.latest.BridgeLongCounterBuilder;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.latest.BridgeLongGaugeBuilder;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.latest.BridgeLongUpDownCounterBuilder;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.latest.BridgeMeter;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.DoubleCounterBuilder;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongGaugeBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;

public class BridgeFactoryLatest extends BridgeFactoryV1_14 {
    private static volatile BridgeFactoryLatest instance;

    public static BridgeFactoryLatest get() {
        if (instance == null) {
            throw new IllegalStateException("Bridge has not been activated yet!");
        }
        return instance;
    }

    public static void activate(BridgeFactoryLatest instanceToActivate) {
        if (instance != null) {
            throw new IllegalStateException("Bridge has already been activated!");
        }
        BridgeFactoryV1_14.activate(instanceToActivate);
        instance = instanceToActivate;
    }

    @Override
    public Meter bridgeMeter(co.elastic.apm.agent.shaded.otel.api.metrics.Meter delegate) {
        return new BridgeMeter(delegate);
    }

    @Override
    public LongGaugeBuilder bridgeLongGaugeBuilder(co.elastic.apm.agent.shaded.otel.api.metrics.LongGaugeBuilder delegate) {
        return new BridgeLongGaugeBuilder(delegate);
    }

    @Override
    public DoubleCounterBuilder bridgeDoubleCounterBuilder(co.elastic.apm.agent.shaded.otel.api.metrics.DoubleCounterBuilder delegate) {
        return new BridgeDoubleCounterBuilder(delegate);
    }

    @Override
    public DoubleUpDownCounterBuilder bridgeDoubleUpDownCounterBuilder(co.elastic.apm.agent.shaded.otel.api.metrics.DoubleUpDownCounterBuilder delegate) {
        return new BridgeDoubleUpDownCounterBuilder(delegate);
    }

    @Override
    public LongCounterBuilder bridgeLongCounterBuilder(co.elastic.apm.agent.shaded.otel.api.metrics.LongCounterBuilder delegate) {
        return new BridgeLongCounterBuilder(delegate);
    }

    @Override
    public LongUpDownCounterBuilder bridgeLongUpDownCounterBuilder(co.elastic.apm.agent.shaded.otel.api.metrics.LongUpDownCounterBuilder delegate) {
        return new BridgeLongUpDownCounterBuilder(delegate);
    }

    @Override
    public DoubleGaugeBuilder bridgeDoubleGaugeBuilder(co.elastic.apm.agent.shaded.otel.api.metrics.DoubleGaugeBuilder delegate) {
        return new BridgeDoubleGaugeBuilder(delegate);
    }

    public BatchCallback bridgeBatchCallback(co.elastic.apm.agent.shaded.otel.api.metrics.BatchCallback delegate) {
        return new BridgeBatchCallback(delegate);
    }
}
