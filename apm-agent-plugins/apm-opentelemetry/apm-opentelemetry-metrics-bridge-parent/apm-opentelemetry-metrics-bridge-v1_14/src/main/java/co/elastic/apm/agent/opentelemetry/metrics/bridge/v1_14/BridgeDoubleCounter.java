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

import co.elastic.apm.agent.embeddedotel.proxy.ProxyDoubleCounter;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.AbstractBridgedElement;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.BridgeFactoryV1_14;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.context.Context;

public class BridgeDoubleCounter extends AbstractBridgedElement<ProxyDoubleCounter> implements DoubleCounter {
    public BridgeDoubleCounter(ProxyDoubleCounter delegate) {
        super(delegate);
    }

    @Override
    public void add(double value) {
        delegate.add(value);
    }

    @Override
    public void add(double value, Attributes attributes) {
        delegate.add(value, BridgeFactoryV1_14.get().convertAttributes(attributes));
    }

    @Override
    public void add(double value, Attributes attributes, Context context) {
        add(value, attributes); //context does not matter for us
    }
}
