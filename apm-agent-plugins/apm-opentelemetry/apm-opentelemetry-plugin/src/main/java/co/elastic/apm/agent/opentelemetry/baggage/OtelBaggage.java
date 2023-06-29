package co.elastic.apm.agent.opentelemetry.baggage;

import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.baggage.BaggageEntryMetadata;

public class OtelBaggage {

    private static final WeakMap<Baggage, co.elastic.apm.agent.impl.baggage.Baggage> translationCache = WeakConcurrent.buildMap();

    public static Baggage fromElasticBaggage(co.elastic.apm.agent.impl.baggage.Baggage elasticBaggage) {
        BaggageBuilder builder = Baggage.builder();
        for (String key : elasticBaggage.keys()) {
            builder.put(key, elasticBaggage.get(key), BaggageEntryMetadata.create(elasticBaggage.getMetadata(key)));
        }
        Baggage result = builder.build();
        // remember the translation for future toElasticBaggage calls
        translationCache.put(result, elasticBaggage);
        return result;
    }

    public static co.elastic.apm.agent.impl.baggage.Baggage toElasticBaggage(Baggage otelBaggage) {
        if (otelBaggage == null || otelBaggage.isEmpty()) {
            return co.elastic.apm.agent.impl.baggage.Baggage.EMPTY;
        }
        co.elastic.apm.agent.impl.baggage.Baggage translated = translationCache.get(otelBaggage);
        if (translated == null) {
            co.elastic.apm.agent.impl.baggage.Baggage.Builder builder = co.elastic.apm.agent.impl.baggage.Baggage.builder();
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
