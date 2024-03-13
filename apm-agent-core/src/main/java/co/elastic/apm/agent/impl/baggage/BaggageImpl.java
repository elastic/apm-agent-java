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
package co.elastic.apm.agent.impl.baggage;

import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.impl.context.AbstractContextImpl;
import co.elastic.apm.agent.impl.transaction.AbstractSpanImpl;
import co.elastic.apm.agent.tracer.Baggage;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BaggageImpl implements Baggage {

    private static final String LIFTED_BAGGAGE_ATTRIBUTE_PREFIX = "baggage.";

    public static final BaggageImpl EMPTY = new BaggageImpl(Collections.<String, String>emptyMap(), Collections.<String, String>emptyMap());

    private final Map<String, String> baggage;

    /**
     * W3C headers allow to add metadata key-value pairs to baggage.
     * We do currently not decode these key-value pairs, but propagate them in encoded form
     * to ensure they are not lost.
     * <p>
     * Keys of this map are guaranteed to also be present as keys in #baggage
     */
    private final Map<String, String> baggageMetadata;

    /**
     * When automatically lifting baggage entries to be stored as span attributes we add the {@link #LIFTED_BAGGAGE_ATTRIBUTE_PREFIX}
     * to the key.
     * Because baggage is usually updated rarely but the lifting can happen for very many spans we cache the prefixed string in this map.
     * See {@link #storeBaggageInAttributes(AbstractSpanImpl, List)} for the implementation details.
     */
    private volatile ConcurrentHashMap<String, String> cachedKeysWithPrefix;

    /**
     * Baggage instances are immutable, therefore we can safely cache the serialized form.
     */
    private volatile String cachedSerializedW3CHeader = null;

    /**
     * Cached UTF8-representation of {@link #cachedSerializedW3CHeader}.
     */
    byte[] cachedSerializedW3CHeaderUtf8 = null;

    private BaggageImpl(Map<String, String> baggage, Map<String, String> baggageMetadata) {
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
     * @return a builder pre-populated with the contents of this baggage.
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


    byte[] getCachedSerializedW3CHeaderUtf8() {
        if (cachedSerializedW3CHeader == null) {
            throw new IllegalStateException("cached string header must be set first");
        }
        if (cachedSerializedW3CHeader.isEmpty()) {
            return null;
        }
        if (cachedSerializedW3CHeaderUtf8 == null) {
            cachedSerializedW3CHeaderUtf8 = cachedSerializedW3CHeader.getBytes(StandardCharsets.UTF_8);
        }
        return this.cachedSerializedW3CHeaderUtf8;
    }

    public void storeBaggageInAttributes(AbstractSpanImpl<?> span, List<WildcardMatcher> keyFilter) {
        if (baggage.isEmpty() || keyFilter.isEmpty()) {
            // early out to prevent unnecessarily allocating an iterator
            return;
        }
        for (String key : baggage.keySet()) {
            if (WildcardMatcher.anyMatch(keyFilter, key) != null) {
                String keyWithPrefix = getKeyWithAttributePrefix(key);
                String value = baggage.get(key);
                span.withOtelAttribute(keyWithPrefix, value);
            }
        }
    }

    public void storeBaggageInContext(AbstractContextImpl context, List<WildcardMatcher> keyFilter) {
        if (baggage.isEmpty() || keyFilter.isEmpty()) {
            // early out to prevent unnecessarily allocating an iterator
            return;
        }
        for (String key : baggage.keySet()) {
            if (WildcardMatcher.anyMatch(keyFilter, key) != null) {
                String keyWithPrefix = getKeyWithAttributePrefix(key);
                String value = baggage.get(key);
                context.addLabel(keyWithPrefix, value);
            }
        }
    }

    private String getKeyWithAttributePrefix(String key) {
        if (cachedKeysWithPrefix == null) {
            //we don't mind the race condition here, at worst we loose a few cached entries which are then recomputed
            cachedKeysWithPrefix = new ConcurrentHashMap<>();
        }
        String keyWithPrefix = cachedKeysWithPrefix.get(key);
        if (keyWithPrefix == null) {
            keyWithPrefix = LIFTED_BAGGAGE_ATTRIBUTE_PREFIX + key;
            cachedKeysWithPrefix.put(key, keyWithPrefix);
        }
        return keyWithPrefix;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BaggageImpl baggage1 = (BaggageImpl) o;

        if (!baggage.equals(baggage1.baggage)) return false;
        return baggageMetadata.equals(baggage1.baggageMetadata);
    }

    @Override
    public int hashCode() {
        int result = baggage.hashCode();
        result = 31 * result + baggageMetadata.hashCode();
        return result;
    }

    public static class Builder {

        public Builder(BaggageImpl parent) {
            this.parent = parent;
            this.baggage = parent.baggage;
            this.baggageMetadata = parent.baggageMetadata;
            buildCalled = false;
        }

        private final BaggageImpl parent;
        private Map<String, String> baggage;
        private Map<String, String> baggageMetadata;

        private boolean buildCalled;

        public Builder put(String key, @Nullable String value) {
            return put(key, value, null);
        }

        public Builder put(String key, @Nullable String value, @Nullable String metadata) {
            if (buildCalled) {
                throw new IllegalStateException("build() was already called!");
            }
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
        public BaggageImpl build() {
            buildCalled = true;
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
                return new BaggageImpl(baggage, baggageMetadata);
            } else {
                return parent;
            }
        }

        private void setBaggageValue(String key, @Nullable String value) {
            if (!Objects.equals(baggage.get(key), value)) {
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
            if (!Objects.equals(baggageMetadata.get(key), metadata)) {
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
