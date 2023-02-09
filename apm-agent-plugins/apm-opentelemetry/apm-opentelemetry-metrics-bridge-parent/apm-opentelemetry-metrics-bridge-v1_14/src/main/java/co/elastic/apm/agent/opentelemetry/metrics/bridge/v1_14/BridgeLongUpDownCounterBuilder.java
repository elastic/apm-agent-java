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

import co.elastic.apm.agent.embeddedotel.proxy.ProxyLongUpDownCounterBuilder;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyObservableLongMeasurement;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.AbstractBridgedElement;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.BridgeFactoryV1_14;
import io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;

import java.util.function.Consumer;

public class BridgeLongUpDownCounterBuilder extends AbstractBridgedElement<ProxyLongUpDownCounterBuilder> implements LongUpDownCounterBuilder {

    public BridgeLongUpDownCounterBuilder(ProxyLongUpDownCounterBuilder delegate) {
        super(delegate);
    }

    @Override
    public LongUpDownCounterBuilder setDescription(String description) {
        delegate.setDescription(description);
        return this;
    }

    @Override
    public LongUpDownCounterBuilder setUnit(String unit) {
        delegate.setUnit(unit);
        return this;
    }

    @Override
    public DoubleUpDownCounterBuilder ofDoubles() {
        return BridgeFactoryV1_14.get().bridgeDoubleUpDownCounterBuilder(delegate.ofDoubles());
    }

    @Override
    public LongUpDownCounter build() {
        return BridgeFactoryV1_14.get().bridgeLongUpDownCounter(delegate.build());
    }

    @Override
    public ObservableLongUpDownCounter buildWithCallback(Consumer<ObservableLongMeasurement> callback) {
        return BridgeFactoryV1_14.get().bridgeObservableLongUpDownCounter(delegate.buildWithCallback(new Consumer<ProxyObservableLongMeasurement>() {
            @Override
            public void accept(ProxyObservableLongMeasurement observableLongMeasurement) {
                callback.accept(BridgeFactoryV1_14.get().bridgeObservableLongMeasurement(observableLongMeasurement));
            }
        }));
    }
}
