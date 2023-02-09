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

import co.elastic.apm.agent.embeddedotel.proxy.ProxyAttributeKey;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyAttributes;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyAttributesBuilder;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyDoubleCounter;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyDoubleCounterBuilder;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyDoubleGaugeBuilder;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyDoubleHistogram;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyDoubleHistogramBuilder;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyDoubleUpDownCounter;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyDoubleUpDownCounterBuilder;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyLongCounter;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyLongCounterBuilder;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyLongGaugeBuilder;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyLongHistogram;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyLongHistogramBuilder;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyLongUpDownCounter;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyLongUpDownCounterBuilder;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyMeter;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyMeterBuilder;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyMeterProvider;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyObservableDoubleCounter;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyObservableDoubleGauge;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyObservableDoubleMeasurement;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyObservableDoubleUpDownCounter;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyObservableLongCounter;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyObservableLongGauge;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyObservableLongMeasurement;
import co.elastic.apm.agent.embeddedotel.proxy.ProxyObservableLongUpDownCounter;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeDoubleCounter;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeDoubleCounterBuilder;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeDoubleGaugeBuilder;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeDoubleHistogram;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeDoubleHistogramBuilder;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeDoubleUpDownCounter;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeDoubleUpDownCounterBuilder;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeLongCounter;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeLongCounterBuilder;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeLongGaugeBuilder;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeLongHistogram;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeLongHistogramBuilder;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeLongUpDownCounter;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeLongUpDownCounterBuilder;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeMeter;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeMeterBuilder;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeMeterProvider;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeObservableDoubleCounter;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeObservableDoubleGauge;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeObservableDoubleMeasurement;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeObservableDoubleUpDownCounter;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeObservableLongCounter;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeObservableLongGauge;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeObservableLongMeasurement;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeObservableLongUpDownCounter;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleCounterBuilder;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongGaugeBuilder;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.metrics.ObservableDoubleCounter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;

import javax.annotation.Nullable;
import java.util.Map;

public class BridgeFactoryV1_14 implements BridgeFactory {

    private static volatile BridgeFactoryV1_14 instance;

    private final WeakMap<AttributeKey<?>, ProxyAttributeKey<?>> convertedAttributeKeys;
    private final WeakMap<io.opentelemetry.api.common.Attributes, ProxyAttributes> convertedAttributes;

    public BridgeFactoryV1_14() {
        convertedAttributeKeys = WeakConcurrent.buildMap();
        convertedAttributes = WeakConcurrent.buildMap();
    }

    public static BridgeFactoryV1_14 get() {
        if (instance == null) {
            throw new IllegalStateException("Bridge has not been activated yet!");
        }
        return instance;
    }

    public static void activate(BridgeFactoryV1_14 instanceToActivate) {
        if (instance != null) {
            throw new IllegalStateException("Bridge has already been activated!");
        }
        instance = instanceToActivate;
    }

    public final ProxyAttributes convertAttributes(Attributes attributes) {
        ProxyAttributes cached = convertedAttributes.get(attributes);
        if (cached == null) {
            cached = doConvertAttributes(attributes);
            if (cached != null) {
                convertedAttributes.put(attributes, cached);
            }
        }
        return cached;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected ProxyAttributes doConvertAttributes(Attributes attributes) {
        ProxyAttributesBuilder builder = ProxyAttributes.builder();
        for (Map.Entry<AttributeKey<?>, Object> attrib : attributes.asMap().entrySet()) {
            AttributeKey<?> key = attrib.getKey();
            Object val = attrib.getValue();
            ProxyAttributeKey bridgedKey = convertAttributeKey(key);
            if (bridgedKey != null) {
                builder.put(bridgedKey, val);
            }
        }
        return builder.build();
    }

    @Nullable
    public ProxyAttributeKey<?> convertAttributeKey(AttributeKey<?> key) {
        ProxyAttributeKey<?> cached = convertedAttributeKeys.get(key);
        if (cached == null) {
            cached = doConvertAttributeKey(key);
            if (cached != null) {
                convertedAttributeKeys.put(key, cached);
            }
        }
        return cached;
    }

    @Nullable
    protected ProxyAttributeKey<?> doConvertAttributeKey(AttributeKey<?> key) {
        switch (key.getType()) {
            case STRING:
                return ProxyAttributeKey.stringKey(key.getKey());
            case BOOLEAN:
                return ProxyAttributeKey.booleanKey(key.getKey());
            case LONG:
                return ProxyAttributeKey.longKey(key.getKey());
            case DOUBLE:
                return ProxyAttributeKey.doubleKey(key.getKey());
            case STRING_ARRAY:
                return ProxyAttributeKey.stringArrayKey(key.getKey());
            case BOOLEAN_ARRAY:
                return ProxyAttributeKey.booleanArrayKey(key.getKey());
            case LONG_ARRAY:
                return ProxyAttributeKey.longArrayKey(key.getKey());
            case DOUBLE_ARRAY:
                return ProxyAttributeKey.doubleArrayKey(key.getKey());
        }
        return null;
    }

    @Override
    public MeterProvider bridgeMeterProvider(ProxyMeterProvider delegate) {
        return new BridgeMeterProvider(delegate);
    }

    public ObservableDoubleMeasurement bridgeObservableDoubleMeasurement(ProxyObservableDoubleMeasurement delegate) {
        return new BridgeObservableDoubleMeasurement(delegate);
    }

    public DoubleCounter bridgeDoubleCounter(ProxyDoubleCounter delegate) {
        return new BridgeDoubleCounter(delegate);
    }

    public ObservableDoubleCounter bridgeObservableDoubleCounter(ProxyObservableDoubleCounter delegate) {
        return new BridgeObservableDoubleCounter(delegate);
    }

    public LongGaugeBuilder bridgeLongGaugeBuilder(ProxyLongGaugeBuilder delegate) {
        return new BridgeLongGaugeBuilder(delegate);
    }

    public ObservableDoubleGauge bridgeObservableDoubleGauge(ProxyObservableDoubleGauge delegate) {
        return new BridgeObservableDoubleGauge(delegate);
    }

    public LongHistogramBuilder bridgeLongHistogramBuilder(ProxyLongHistogramBuilder delegate) {
        return new BridgeLongHistogramBuilder(delegate);
    }

    public DoubleHistogram bridgeDoubleHistogram(ProxyDoubleHistogram delegate) {
        return new BridgeDoubleHistogram(delegate);
    }

    public DoubleUpDownCounter bridgeDoubleUpDownCounter(ProxyDoubleUpDownCounter delegate) {
        return new BridgeDoubleUpDownCounter(delegate);
    }

    public ObservableDoubleUpDownCounter bridgeObservableDoubleUpDownCounter(ProxyObservableDoubleUpDownCounter delegate) {
        return new BridgeObservableDoubleUpDownCounter(delegate);
    }

    public DoubleCounterBuilder bridgeDoubleCounterBuilder(ProxyDoubleCounterBuilder delegate) {
        return new BridgeDoubleCounterBuilder(delegate);
    }

    public LongCounter bridgeLongCounter(ProxyLongCounter delegate) {
        return new BridgeLongCounter(delegate);
    }

    public ObservableLongCounter bridgeObservableLongCounter(ProxyObservableLongCounter delegate) {
        return new BridgeObservableLongCounter(delegate);
    }

    public ObservableLongMeasurement bridgeObservableLongMeasurement(ProxyObservableLongMeasurement delegate) {
        return new BridgeObservableLongMeasurement(delegate);
    }

    public ObservableLongGauge bridgeObservableLongGauge(ProxyObservableLongGauge delegate) {
        return new BridgeObservableLongGauge(delegate);
    }

    public LongHistogram bridgeLongHistogram(ProxyLongHistogram delegate) {
        return new BridgeLongHistogram(delegate);
    }

    public DoubleUpDownCounterBuilder bridgeDoubleUpDownCounterBuilder(ProxyDoubleUpDownCounterBuilder delegate) {
        return new BridgeDoubleUpDownCounterBuilder(delegate);
    }

    public LongUpDownCounter bridgeLongUpDownCounter(ProxyLongUpDownCounter delegate) {
        return new BridgeLongUpDownCounter(delegate);
    }

    public ObservableLongUpDownCounter bridgeObservableLongUpDownCounter(ProxyObservableLongUpDownCounter delegate) {
        return new BridgeObservableLongUpDownCounter(delegate);
    }

    public LongCounterBuilder bridgeLongCounterBuilder(ProxyLongCounterBuilder delegate) {
        return new BridgeLongCounterBuilder(delegate);
    }

    public LongUpDownCounterBuilder bridgeLongUpDownCounterBuilder(ProxyLongUpDownCounterBuilder delegate) {
        return new BridgeLongUpDownCounterBuilder(delegate);
    }

    public DoubleHistogramBuilder bridgeDoubleHistogramBuilder(ProxyDoubleHistogramBuilder delegate) {
        return new BridgeDoubleHistogramBuilder(delegate);
    }

    public DoubleGaugeBuilder bridgeDoubleGaugeBuilder(ProxyDoubleGaugeBuilder delegate) {
        return new BridgeDoubleGaugeBuilder(delegate);
    }

    public Meter bridgeMeter(ProxyMeter delegate) {
        return new BridgeMeter(delegate);
    }

    public MeterBuilder bridgeMeterBuilder(ProxyMeterBuilder delegate) {
        return new BridgeMeterBuilder(delegate);
    }

}
