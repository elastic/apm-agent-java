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

import co.elastic.apm.agent.opentelemetry.metrics.bridge.AbstractBridgedElement;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.BridgeFactoryV1_14;
import io.opentelemetry.api.metrics.DoubleCounterBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;

import java.util.function.Consumer;

public class BridgeLongCounterBuilder extends AbstractBridgedElement<co.elastic.apm.agent.shaded.otel.api.metrics.LongCounterBuilder> implements LongCounterBuilder {

    public BridgeLongCounterBuilder(co.elastic.apm.agent.shaded.otel.api.metrics.LongCounterBuilder delegate) {
        super(delegate);
    }

    @Override
    public LongCounterBuilder setDescription(String description) {
        delegate.setDescription(description);
        return this;
    }

    @Override
    public LongCounterBuilder setUnit(String unit) {
        delegate.setUnit(unit);
        return this;
    }

    @Override
    public DoubleCounterBuilder ofDoubles() {
        return BridgeFactoryV1_14.get().bridgeDoubleCounterBuilder(delegate.ofDoubles());
    }

    @Override
    public LongCounter build() {
        return BridgeFactoryV1_14.get().bridgeLongCounter(delegate.build());
    }

    @Override
    public ObservableLongCounter buildWithCallback(Consumer<ObservableLongMeasurement> callback) {
        return BridgeFactoryV1_14.get().bridgeObservableLongCounter(delegate.buildWithCallback(new Consumer<co.elastic.apm.agent.shaded.otel.api.metrics.ObservableLongMeasurement>() {
            @Override
            public void accept(co.elastic.apm.agent.shaded.otel.api.metrics.ObservableLongMeasurement observableLongMeasurement) {
                callback.accept(BridgeFactoryV1_14.get().bridgeObservableLongMeasurement(observableLongMeasurement));
            }
        }));
    }
    
}
