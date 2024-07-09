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
package co.elastic.apm.agent.opentelemetry.baggage;

import co.elastic.apm.agent.impl.baggage.BaggageImpl;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.baggage.BaggageEntryMetadata;

public class OtelBaggage {

    private static final WeakMap<Baggage, BaggageImpl> translationCache = WeakConcurrent.buildMap();

    public static Baggage fromElasticBaggage(BaggageImpl elasticBaggage) {
        BaggageBuilder builder = Baggage.builder();
        for (String key : elasticBaggage.keys()) {
            builder.put(key, elasticBaggage.get(key), BaggageEntryMetadata.create(elasticBaggage.getMetadata(key)));
        }
        Baggage result = builder.build();
        // remember the translation for future toElasticBaggage calls
        translationCache.put(result, elasticBaggage);
        return result;
    }

    public static BaggageImpl toElasticBaggage(Baggage otelBaggage) {
        if (otelBaggage == null || otelBaggage.isEmpty()) {
            return BaggageImpl.EMPTY;
        }
        BaggageImpl translated = translationCache.get(otelBaggage);
        if (translated == null) {
            BaggageImpl.Builder builder = BaggageImpl.builder();
            otelBaggage.forEach((key, value) -> {
                String metadata = value.getMetadata().getValue();
                builder.put(key, value.getValue(), metadata.isEmpty() ? null : metadata);
            });
            translated = builder.build();
            translationCache.put(otelBaggage, translated);
        }
        return translated;
    }
}
