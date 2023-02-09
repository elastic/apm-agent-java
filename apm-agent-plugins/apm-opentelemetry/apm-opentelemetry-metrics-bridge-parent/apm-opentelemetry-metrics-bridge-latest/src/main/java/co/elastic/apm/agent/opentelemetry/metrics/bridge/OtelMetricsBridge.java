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

import co.elastic.apm.agent.embeddedotel.proxy.ProxyMeterProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;

import java.lang.reflect.Method;

public class OtelMetricsBridge {

    private static BridgeFactory factory;

    public static MeterProvider create(ProxyMeterProvider delegate) {
        return getFactory().bridgeMeterProvider(delegate);
    }

    private static BridgeFactory getFactory() {
        if (factory == null) {
            selectBridgeForClasspathOtelApi();
        }
        return factory;
    }

    private synchronized static void selectBridgeForClasspathOtelApi() {
        if (factory != null) {
            return;
        }
        // Meter.batchCallback was added in otel version 1.15
        if (publicMethodExists(Meter.class, "batchCallback")) {
            BridgeFactoryLatest result = new BridgeFactoryLatest();
            BridgeFactoryLatest.activate(result);
            factory = result;
        } else {
            BridgeFactoryV1_14 result = new BridgeFactoryV1_14();
            BridgeFactoryV1_14.activate(result);
            factory = result;
        }
    }

    private static boolean publicMethodExists(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
