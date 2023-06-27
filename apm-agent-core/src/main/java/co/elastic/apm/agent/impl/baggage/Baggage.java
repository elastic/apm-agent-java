package co.elastic.apm.agent.impl.baggage;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Baggage implements co.elastic.apm.agent.tracer.Baggage {

    public static final Baggage EMPTY = new Baggage(Collections.emptyMap(), Collections.emptyMap());

    private final Map<String, String> baggage;

    /**
     * W3C headers allow to add metadata key-value paris to baggage.
     * We do currently not decode these key-value pairs, but propagate them in encoded form
     * to ensure they are not lost.
     * <p>
     * Keys of this map are guaranteed to also be present as keys in #baggage
     */
    private final Map<String, String> baggageMetadata;

    /**
     * Baggage instances are immutable, therefore we can safely cache the serialized form.
     */
    private volatile String cachedSerializedW3CHeader = null;

    private Baggage(Map<String, String> baggage, Map<String, String> baggageMetadata) {
        this.baggage = baggage;
        this.baggageMetadata = baggageMetadata;
    }

    public Set<String> keys() {
        return baggage.keySet();
    }

    public @Nullable String get(String key) {
        return baggage.get(key);
    }

    @Nullable
    public String getMetadata(String key) {
        return baggageMetadata.get(key);
    }

    public boolean isEmpty() {
        return baggage.isEmpty();
    }

    public static Builder builder() {
        return EMPTY.toBuilder();
    }

    /**
     * @return a builder prepoluated with the contets of this baggage.
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    void setCachedSerializedW3CHeader(String serialized) {
        this.cachedSerializedW3CHeader = serialized;
    }

    String getCachedSerializedW3CHeader() {
        return this.cachedSerializedW3CHeader;
    }

    public static class Builder {

        public Builder(Baggage parent) {
            this.parent = parent;
            this.baggage = parent.baggage;
            this.baggageMetadata = parent.baggageMetadata;
        }

        private Baggage parent;
        private Map<String, String> baggage;
        private Map<String, String> baggageMetadata;

        public Builder put(String key, @Nullable String value) {
            return put(key, value, null);
        }

        public Builder put(String key, @Nullable String value, @Nullable String metadata) {
            if (value == null) {
                setBaggageValue(key, null);
                setBaggageMetadata(key, null);
            } else {
                setBaggageValue(key, value);
                setBaggageMetadata(key, metadata);
            }
            return this;
        }

        /**
         * Builds the resulting baggage.
         * If no modifications were done, the returned instance might be the same as the parent baggage.
         * The builder must not be used anymore after calling build.
         *
         * @return a baggage resulting from this builder.
         */
        public Baggage build() {
            boolean anyModifications = false;
            if (baggage != parent.baggage) {
                anyModifications = true;
                baggage = Collections.unmodifiableMap(baggage);
            }
            if (baggageMetadata != parent.baggageMetadata) {
                anyModifications = true;
                baggageMetadata = Collections.unmodifiableMap(baggageMetadata);
            }
            if (anyModifications) {
                return new Baggage(baggage, baggageMetadata);
            } else {
                return parent;
            }
        }

        private void setBaggageValue(String key, @Nullable String value) {
            if (!Objects.equals(baggage.get(key), key)) {
                if (baggage == parent.baggage) {
                    baggage = new LinkedHashMap<>(baggage);
                }
                if (value == null) {
                    baggage.remove(key);
                } else {
                    baggage.put(key, value);
                }
            }
        }

        private void setBaggageMetadata(String key, @Nullable String metadata) {
            if (!Objects.equals(baggageMetadata.get(key), key)) {
                if (baggageMetadata == parent.baggageMetadata) {
                    baggageMetadata = new LinkedHashMap<>(baggageMetadata);
                }
                if (metadata == null) {
                    baggageMetadata.remove(key);
                } else {
                    baggageMetadata.put(key, metadata);
                }
            }
        }
    }
}
