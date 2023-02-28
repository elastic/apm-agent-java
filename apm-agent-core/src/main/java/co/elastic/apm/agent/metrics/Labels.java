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
package co.elastic.apm.agent.metrics;

import co.elastic.apm.agent.tracer.pooling.Recyclable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Labels are key/value pairs and relate to <a href="https://www.elastic.co/guide/en/ecs/current/ecs-base.html#_base_field_details">ECS labels</a>.
 * However, there are also top-level labels which are not nested under the {@code labels} object,
 * for example {@link #getTransactionName()}, {@link #getTransactionType()}, {@link #getSpanType()} and {@link #getSpanSubType()}.
 * <p>
 * Metrics are structured into multiple {@link MetricSet}s.
 * For each distinct combination of {@link Labels}, there is one {@link MetricSet}.
 * </p>
 * <p>
 * Labels allow for {@link CharSequence}s as a value,
 * thus avoiding allocations for {@code transaction.name.toString()} when tracking breakdown metrics for a transaction.
 * Iterations over the labels also don't allocate an Iterator, in contrast to {@code Map.entrySet().iterator()}.
 * </p>
 */
public interface Labels {

    Labels EMPTY = Labels.Immutable.empty();

    @Nullable
    String getServiceName();

    @Nullable
    String getServiceVersion();

    @Nullable
    CharSequence getTransactionName();

    @Nullable
    String getTransactionType();

    @Nullable
    String getSpanType();

    @Nullable
    String getSpanSubType();

    List<String> getKeys();

    List<CharSequence> getValues();

    boolean isEmpty();

    int size();

    String getKey(int i);

    CharSequence getValue(int i);

    Labels.Immutable immutableCopy();

    abstract class AbstractBase implements Labels {
        protected final List<String> keys;
        protected final List<CharSequence> values;

        AbstractBase(List<String> keys, List<CharSequence> values) {
            this.keys = keys;
            this.values = values;
        }

        public List<String> getKeys() {
            return keys;
        }

        public List<CharSequence> getValues() {
            return values;
        }

        public boolean isEmpty() {
            return keys.isEmpty() && getServiceName() == null && getServiceVersion() == null && getTransactionName() == null && getTransactionType() == null && getSpanType() == null;
        }

        public int size() {
            return keys.size();
        }

        public String getKey(int i) {
            return keys.get(i);
        }

        public CharSequence getValue(int i) {
            return values.get(i);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Labels)) return false;
            AbstractBase labels = (AbstractBase) o;
            return Objects.equals(getSpanType(), labels.getSpanType()) &&
                Objects.equals(getSpanSubType(), labels.getSpanSubType()) &&
                Objects.equals(getTransactionType(), labels.getTransactionType()) &&
                contentEquals(getTransactionName(), labels.getTransactionName()) &&
                Objects.equals(getServiceName(), labels.getServiceName()) &&
                Objects.equals(getServiceVersion(), labels.getServiceVersion()) &&
                keys.equals(labels.keys) &&
                isEqual(values, labels.values);
        }

        @Override
        public int hashCode() {
            int h = 0;
            for (int i = 0; i < values.size(); i++) {
                h = 31 * h + hashEntryAt(i);
            }
            h = 31 * h + hash(getServiceName());
            h = 31 * h + hash(getServiceVersion());
            h = 31 * h + hash(getTransactionName());
            h = 31 * h + (getTransactionType() != null ? getTransactionType().hashCode() : 0);
            h = 31 * h + (getSpanType() != null ? getSpanType().hashCode() : 0);
            h = 31 * h + (getSpanSubType() != null ? getSpanSubType().hashCode() : 0);
            return h;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(keys.get(i)).append("=").append(values.get(i));

            }
            return sb.toString();
        }

        private int hashEntryAt(int i) {
            return keys.get(i).hashCode() * 31 + hash(values.get(i));
        }

        private static boolean isEqual(List<CharSequence> values, List<CharSequence> otherValues) {
            if (values.size() != otherValues.size()) {
                return false;
            }
            for (int i = 0; i < values.size(); i++) {
                if (!contentEquals(values.get(i), otherValues.get(i))) {
                    return false;
                }
            }
            return true;
        }

        private static boolean contentEquals(@Nullable CharSequence cs1, @Nullable CharSequence cs2) {
            if (cs1 == null || cs2 == null) {
                return cs1 == cs2;
            }
            if (cs1 instanceof String) {
                return ((String) cs1).contentEquals(cs2);
            } else if (cs2 instanceof String) {
                return ((String) cs2).contentEquals(cs1);
            } else {
                if (cs1.length() == cs2.length()) {
                    for (int i = 0; i < cs1.length(); i++) {
                        if (cs1.charAt(i) != cs2.charAt(i)) {
                            return false;
                        }
                    }
                    return true;
                }
            }
            return false;
        }

        static int hash(@Nullable CharSequence cs) {
            if (cs == null) {
                return 0;
            }
            // this is safe as the hash code calculation is well defined
            // (see javadoc for String.hashCode())
            if (cs instanceof String) return cs.hashCode();
            int h = 0;
            for (int i = 0; i < cs.length(); i++) {
                h = 31 * h + cs.charAt(i);
            }
            return h;
        }
    }

    class Mutable extends AbstractBase implements Recyclable {

        @Nullable
        private String serviceName;
        @Nullable
        private String serviceVersion;
        @Nullable
        private CharSequence transactionName;
        @Nullable
        private String transactionType;
        @Nullable
        private String spanType;
        @Nullable
        private String spanSubType;

        private Mutable() {
            super(new ArrayList<String>(), new ArrayList<CharSequence>());
        }

        public static Mutable of() {
            return new Mutable();
        }

        public static Mutable of(String key, CharSequence value) {
            final Mutable labels = new Mutable();
            labels.add(key, value);
            return labels;
        }

        public static Mutable of(Map<String, ? extends CharSequence> labelMap) {
            Mutable labels = new Mutable();
            for (Map.Entry<String, ? extends CharSequence> entry : labelMap.entrySet()) {
                labels.add(entry.getKey(), entry.getValue());
            }
            return labels;
        }

        public Labels add(String key, CharSequence value) {
            keys.add(key);
            values.add(value);
            return this;
        }

        public Labels.Mutable serviceName(@Nullable String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Labels.Mutable serviceVersion(@Nullable String serviceVersion) {
            this.serviceVersion = serviceVersion;
            return this;
        }

        public Labels.Mutable transactionName(@Nullable CharSequence transactionName) {
            this.transactionName = transactionName;
            return this;
        }

        public Labels.Mutable transactionType(@Nullable String transactionType) {
            this.transactionType = transactionType;
            return this;
        }

        public Labels.Mutable spanType(@Nullable String spanType) {
            this.spanType = spanType;
            return this;
        }

        public Labels.Mutable spanSubType(@Nullable String subtype) {
            this.spanSubType = subtype;
            return this;
        }

        @Nullable
        public String getServiceName() {
            return serviceName;
        }

        @Nullable
        public String getServiceVersion() {
            return serviceVersion;
        }

        @Nullable
        public CharSequence getTransactionName() {
            return transactionName;
        }

        @Nullable
        public String getTransactionType() {
            return transactionType;
        }

        @Nullable
        public String getSpanType() {
            return spanType;
        }

        @Override
        @Nullable
        public String getSpanSubType() {
            return spanSubType;
        }

        public Labels.Immutable immutableCopy() {
            return new Immutable(this);
        }

        @Override
        public void resetState() {
            keys.clear();
            values.clear();
            serviceName = null;
            serviceVersion = null;
            transactionName = null;
            transactionType = null;
            spanType = null;
            spanSubType = null;
        }
    }

    /**
     * An immutable implementation of the {@link Labels} interface
     * <p>
     * To publish a copy of {@link Mutable} in a thread-safe manner,
     * all properties need to be final.
     * That's why we can't share the exact same class.
     * </p>
     */
    class Immutable extends AbstractBase {
        private static final Labels.Immutable EMPTY = new Mutable().immutableCopy();

        private final int hash;
        @Nullable
        private final String serviceName;
        @Nullable
        private final String serviceVersion;
        @Nullable
        private final String transactionName;
        @Nullable
        private final String transactionType;
        @Nullable
        private final String spanType;
        @Nullable
        private final String spanSubType;

        public Immutable(Labels labels) {
            super(new ArrayList<>(labels.getKeys()), copy(labels.getValues()));
            this.serviceName = labels.getServiceName();
            this.serviceVersion = labels.getServiceVersion();
            final CharSequence transactionName = labels.getTransactionName();
            this.transactionName = transactionName != null ? transactionName.toString() : null;
            this.transactionType = labels.getTransactionType();
            this.spanType = labels.getSpanType();
            this.spanSubType = labels.getSpanSubType();
            this.hash = labels.hashCode();
        }

        private static List<CharSequence> copy(List<CharSequence> values) {
            List<CharSequence> immutableValues = new ArrayList<>(values.size());
            for (int i = 0; i < values.size(); i++) {
                immutableValues.add(values.get(i).toString());
            }
            return immutableValues;
        }

        public static Labels.Immutable empty() {
            return EMPTY;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Nullable
        @Override
        public String getServiceName() {
            return serviceName;
        }

        @Nullable
        @Override
        public String getServiceVersion() {
            return serviceVersion;
        }

        @Nullable
        @Override
        public String getTransactionName() {
            return transactionName;
        }

        @Nullable
        @Override
        public String getTransactionType() {
            return transactionType;
        }

        @Nullable
        @Override
        public String getSpanType() {
            return spanType;
        }

        @Override
        @Nullable
        public String getSpanSubType() {
            return spanSubType;
        }

        @Override
        public Labels.Immutable immutableCopy() {
            return this;
        }
    }

}
