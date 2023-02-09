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
package co.elastic.apm.agent.embeddedotel.proxy;

import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;

import java.util.function.Consumer;

public class ProxyDoubleGaugeBuilder {

    private final DoubleGaugeBuilder delegate;

    public ProxyDoubleGaugeBuilder(DoubleGaugeBuilder delegate) {
        this.delegate = delegate;
    }

    public DoubleGaugeBuilder getDelegate() {
        return delegate;
    }

    public ProxyLongGaugeBuilder ofLongs() {
        return new ProxyLongGaugeBuilder(delegate.ofLongs());
    }

    public ProxyDoubleGaugeBuilder setUnit(String arg0) {
        delegate.setUnit(arg0);
        return this;
    }

    public ProxyObservableDoubleMeasurement buildObserver() {
        return new ProxyObservableDoubleMeasurement(delegate.buildObserver());
    }

    public ProxyDoubleGaugeBuilder setDescription(String arg0) {
        delegate.setDescription(arg0);
        return this;
    }

    public ProxyObservableDoubleGauge buildWithCallback(Consumer<ProxyObservableDoubleMeasurement> arg0) {
        return new ProxyObservableDoubleGauge(delegate.buildWithCallback(new Consumer<ObservableDoubleMeasurement>(){
            @Override
            public void accept(ObservableDoubleMeasurement observableDoubleMeasurement) {
                arg0.accept(new ProxyObservableDoubleMeasurement(observableDoubleMeasurement));
            }
        }));
    }

}
