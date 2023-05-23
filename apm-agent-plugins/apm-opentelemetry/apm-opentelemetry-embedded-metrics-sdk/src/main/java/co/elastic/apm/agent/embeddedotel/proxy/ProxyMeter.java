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

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableMeasurement;

public class ProxyMeter {

    private final Meter delegate;

    public ProxyMeter(Meter delegate) {
        this.delegate = delegate;
    }

    public Meter getDelegate() {
        return delegate;
    }

    public ProxyLongCounterBuilder counterBuilder(String arg0) {
        return new ProxyLongCounterBuilder(delegate.counterBuilder(arg0));
    }

    public ProxyLongUpDownCounterBuilder upDownCounterBuilder(String arg0) {
        return new ProxyLongUpDownCounterBuilder(delegate.upDownCounterBuilder(arg0));
    }

    public ProxyDoubleGaugeBuilder gaugeBuilder(String arg0) {
        return new ProxyDoubleGaugeBuilder(delegate.gaugeBuilder(arg0));
    }

    public ProxyBatchCallback batchCallback(Runnable arg0, ProxyObservableMeasurement arg1, ProxyObservableMeasurement[] arg2) {
        ObservableMeasurement[] unboxed = new ObservableMeasurement[arg2.length];
        for (int i = 0; i < arg2.length; i++) {
            unboxed[i] = arg2[i].getDelegate();
        }
        return new ProxyBatchCallback(delegate.batchCallback(arg0, arg1.getDelegate(), unboxed));
    }

    public ProxyDoubleHistogramBuilder histogramBuilder(String arg0) {
        return new ProxyDoubleHistogramBuilder(delegate.histogramBuilder(arg0));
    }

}
