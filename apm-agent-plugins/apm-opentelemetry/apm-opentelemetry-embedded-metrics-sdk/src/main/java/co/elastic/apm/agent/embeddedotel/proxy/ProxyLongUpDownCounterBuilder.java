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

import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;

import java.util.function.Consumer;

public class ProxyLongUpDownCounterBuilder {

    private final LongUpDownCounterBuilder delegate;

    public ProxyLongUpDownCounterBuilder(LongUpDownCounterBuilder delegate) {
        this.delegate = delegate;
    }

    public LongUpDownCounterBuilder getDelegate() {
        return delegate;
    }

    public ProxyLongUpDownCounter build() {
        return new ProxyLongUpDownCounter(delegate.build());
    }

    public ProxyDoubleUpDownCounterBuilder ofDoubles() {
        return new ProxyDoubleUpDownCounterBuilder(delegate.ofDoubles());
    }

    public ProxyLongUpDownCounterBuilder setUnit(String arg0) {
        delegate.setUnit(arg0);
        return this;
    }

    public ProxyObservableLongMeasurement buildObserver() {
        return new ProxyObservableLongMeasurement(delegate.buildObserver());
    }

    public ProxyLongUpDownCounterBuilder setDescription(String arg0) {
        delegate.setDescription(arg0);
        return this;
    }

    public ProxyObservableLongUpDownCounter buildWithCallback(Consumer<ProxyObservableLongMeasurement> arg0) {
        return new ProxyObservableLongUpDownCounter(delegate.buildWithCallback(new Consumer<ObservableLongMeasurement>() {
            @Override
            public void accept(ObservableLongMeasurement observableLongMeasurement) {
                arg0.accept(new ProxyObservableLongMeasurement(observableLongMeasurement));
            }
        }));
    }

}
