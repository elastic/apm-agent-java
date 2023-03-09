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
import io.opentelemetry.api.metrics.MeterBuilder;

public class ProxyMeterBuilder {

    private final MeterBuilder delegate;

    public ProxyMeterBuilder(MeterBuilder delegate) {
        this.delegate = delegate;
    }

    public MeterBuilder getDelegate() {
        return delegate;
    }

    public ProxyMeter build() {
        return new ProxyMeter(delegate.build());
    }

    public ProxyMeterBuilder setSchemaUrl(String arg0) {
        delegate.setSchemaUrl(arg0);
        return this;
    }

    public ProxyMeterBuilder setInstrumentationVersion(String arg0) {
        delegate.setInstrumentationVersion(arg0);
        return this;
    }

}