/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.metrics;

import co.elastic.apm.agent.objectpool.Recyclable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Labels implements Recyclable {

    private static final Labels EMPTY = Labels.of().immutableCopy();
    private final List<String> keys = new ArrayList<>();
    private final List<CharSequence> values = new ArrayList<>();
    private final boolean immutable;
    @Nullable
    private CharSequence transactionName;
    @Nullable
    private String transactionType;
    @Nullable
    private String spanType;
    private int cachedHash;

    public Labels() {
        this(Collections.<String>emptyList(), Collections.<CharSequence>emptyList(), false);
    }

    private Labels(List<String> keys, List<? extends CharSequence> values, boolean immutable) {
        this.keys.addAll(keys);
        this.values.addAll(values);
        this.immutable = immutable;
    }

    public static Labels of() {
        return new Labels();
    }

    public static Labels of(String key, CharSequence value) {
        final Labels labels = new Labels();
        labels.add(key, value);
        return labels;
    }

    public static Labels of(Map<String, ? extends CharSequence> labelMap) {
        Labels labels = new Labels();
        for (Map.Entry<String, ? extends CharSequence> entry : labelMap.entrySet()) {
            labels.add(entry.getKey(), entry.getValue());
        }
        return labels;
    }

    public static Labels empty() {
        return EMPTY;
    }

    public Labels add(String key, CharSequence value) {
        assertMutable();
        keys.add(key);
        values.add(value);
        return this;
    }

    public Labels transactionName(@Nullable CharSequence transactionName) {
        assertMutable();
        this.transactionName = transactionName;
        return this;
    }

    public Labels transactionType(@Nullable String transactionType) {
        assertMutable();
        this.transactionType = transactionType;
        return this;
    }

    public Labels spanType(@Nullable String spanType) {
        assertMutable();
        this.spanType = spanType;
        return this;
    }

    private void assertMutable() {
        if (immutable) {
            throw new UnsupportedOperationException("This Labels instance is immutable");
        }
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

    public Labels immutableCopy() {
        List<String> immutableValues = new ArrayList<>(values.size());
        for (int i = 0; i < keys.size(); i++) {
            immutableValues.add(values.get(i).toString());
        }
        final Labels labels = new Labels(keys, immutableValues, true);
        labels.transactionName = this.transactionName != null ? this.transactionName.toString() : null;
        labels.transactionType = this.transactionType;
        labels.spanType = this.spanType;
        labels.cachedHash = labels.hashCode();
        return labels;
    }

    public List<String> getKeys() {
        return keys;
    }

    public List<CharSequence> getValues() {
        return values;
    }

    public boolean isEmpty() {
        return keys.isEmpty() && transactionName == null && transactionType == null && spanType == null;
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
        if (o == null || getClass() != o.getClass()) return false;
        Labels labels = (Labels) o;
        return Objects.equals(spanType, labels.spanType) &&
            Objects.equals(transactionType, labels.transactionType) &&
            contentEquals(transactionName, labels.transactionName) &&
            keys.equals(labels.keys) &&
            isEqual(values, labels.values);
    }

    @Override
    public int hashCode() {
        if (cachedHash != 0) {
            return cachedHash;
        }
        int h = 0;
        for (int i = 0; i < values.size(); i++) {
            h = 31 * h + hash(i);
        }
        h = 31 * h + hash(transactionName);
        h = 31 * h + (transactionType != null ? transactionType.hashCode() : 0);
        h = 31 * h + (spanType != null ? spanType.hashCode() : 0);
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

    private int hash(int i) {
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

    @Override
    public void resetState() {
        keys.clear();
        values.clear();
        transactionName = null;
        transactionType = null;
        spanType = null;
    }
}
