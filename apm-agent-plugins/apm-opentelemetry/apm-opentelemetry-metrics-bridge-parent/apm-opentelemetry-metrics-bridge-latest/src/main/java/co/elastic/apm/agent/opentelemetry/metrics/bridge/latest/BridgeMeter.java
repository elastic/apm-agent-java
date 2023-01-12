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
package co.elastic.apm.agent.opentelemetry.metrics.bridge.latest;

import co.elastic.apm.agent.opentelemetry.metrics.bridge.BridgeFactoryLatest;
import co.elastic.apm.agent.opentelemetry.metrics.bridge.BridgedElement;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableMeasurement;

public class BridgeMeter extends co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeMeter implements Meter {

    public BridgeMeter(co.elastic.apm.agent.shaded.otel.api.metrics.Meter delegate) {
        super(delegate);
    }

    @Override
    @SuppressWarnings("unchecked")
    public BatchCallback batchCallback(Runnable callback, ObservableMeasurement observableMeasurement, ObservableMeasurement... additionalMeasurements) {

        if (!(observableMeasurement instanceof BridgedElement)) {
            throw new IllegalStateException("Provided observable measurement is from a different SDK!");
        }
        co.elastic.apm.agent.shaded.otel.api.metrics.ObservableMeasurement m1 =
            ((BridgedElement<? extends co.elastic.apm.agent.shaded.otel.api.metrics.ObservableMeasurement>) observableMeasurement).unwrapBridge();

        co.elastic.apm.agent.shaded.otel.api.metrics.ObservableMeasurement[] m2
            = new co.elastic.apm.agent.shaded.otel.api.metrics.ObservableMeasurement[additionalMeasurements.length];
        for (int i = 0; i < additionalMeasurements.length; i++) {
            ObservableMeasurement measurement = additionalMeasurements[i];
            if (!(measurement instanceof BridgedElement)) {
                throw new IllegalStateException("Provided observable measurement is from a different SDK!");
            }
            m2[i] = ((BridgedElement<? extends co.elastic.apm.agent.shaded.otel.api.metrics.ObservableMeasurement>) measurement).unwrapBridge();
        }

        return BridgeFactoryLatest.get().bridgeBatchCallback(delegate.batchCallback(callback, m1, m2));
    }
}
