package co.elastic.apm.agent.tracer;

import javax.annotation.Nullable;

/**
 * Builder for creating a context with alteredBaggage.
 */
public interface BaggageContextBuilder {

    /**
     * @param key   The key of the baggage to store
     * @param value the updated value. If null, any present value for the given key will be removed.
     */
    void put(String key, @Nullable String value);

    /**
     * Same as invoking {@link #put(String, String)} with a null value.
     *
     * @param key
     */
    void remove(String key);

    /**
     * @return the created context with the baggage updates applied.
     */
    ElasticContext<?> buildContext();
}
