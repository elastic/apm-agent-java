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
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.LongGaugeBuilder;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;

import java.util.function.Consumer;

public class BridgeDoubleGaugeBuilder extends AbstractBridgedElement<co.elastic.apm.agent.shaded.otel.api.metrics.DoubleGaugeBuilder> implements DoubleGaugeBuilder {

    public BridgeDoubleGaugeBuilder(co.elastic.apm.agent.shaded.otel.api.metrics.DoubleGaugeBuilder delegate) {
        super(delegate);
    }

    @Override
    public DoubleGaugeBuilder setDescription(String description) {
        delegate.setDescription(description);
        return this;
    }

    @Override
    public DoubleGaugeBuilder setUnit(String unit) {
        delegate.setUnit(unit);
        return this;
    }

    @Override
    public LongGaugeBuilder ofLongs() {
        return BridgeFactoryV1_14.get().bridgeLongGaugeBuilder(delegate.ofLongs());
    }

    @Override
    public ObservableDoubleGauge buildWithCallback(Consumer<ObservableDoubleMeasurement> callback) {
        return BridgeFactoryV1_14.get().bridgeObservableDoubleGauge(delegate.buildWithCallback(new Consumer<co.elastic.apm.agent.shaded.otel.api.metrics.ObservableDoubleMeasurement>() {
            @Override
            public void accept(co.elastic.apm.agent.shaded.otel.api.metrics.ObservableDoubleMeasurement observableDoubleMeasurement) {
                callback.accept(BridgeFactoryV1_14.get().bridgeObservableDoubleMeasurement(observableDoubleMeasurement));
            }
        }));
    }
}
