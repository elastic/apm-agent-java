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
package co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14;

import co.elastic.apm.agent.embeddedotel.proxy.ProxyMeter;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.AbstractBridgedElement;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.BridgeFactoryV1_14;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;

public class BridgeMeter extends AbstractBridgedElement<ProxyMeter> implements Meter {

    public BridgeMeter(ProxyMeter delegate) {
        super(delegate);
    }

    @Override
    public LongCounterBuilder counterBuilder(String name) {
        return BridgeFactoryV1_14.get().bridgeLongCounterBuilder(delegate.counterBuilder(name));
    }

    @Override
    public LongUpDownCounterBuilder upDownCounterBuilder(String name) {
        return BridgeFactoryV1_14.get().bridgeLongUpDownCounterBuilder(delegate.upDownCounterBuilder(name));
    }

    @Override
    public DoubleHistogramBuilder histogramBuilder(String name) {
        return BridgeFactoryV1_14.get().bridgeDoubleHistogramBuilder(delegate.histogramBuilder(name));
    }

    @Override
    public DoubleGaugeBuilder gaugeBuilder(String name) {
        return BridgeFactoryV1_14.get().bridgeDoubleGaugeBuilder(delegate.gaugeBuilder(name));
    }
}
