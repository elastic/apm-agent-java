/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.objectpool.Recyclable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class TraceState implements Recyclable {

    private static final String VENDOR_ENTRIES_SEPARATOR = ",";
    private static final String ENTRY_SEPARATOR = ";";
    private static final String KEY_VALUE_SEPARATOR = ":";
    private static final String VENDOR_PREFIX = "es=";
    private static final String SAMPLE_RATE_KEY = "s";

    /**
     * List of tracestate header values
     */
    private final List<String> tracestate;

    /**
     * sample rate, null if unknown or not set
     */
    @Nullable
    private Double sampleRate;

    /**
     * Key/Value pairs for entries in the 'es' namespace
     */
    private final Map<String, String> entries;

    /**
     * where elastic apm header is stored (if any) within {@link #tracestate} list.
     */
    private int index;

    /**
     * {@literal true} when tracestate array needs an update at known index
     */
    private boolean needsUpdate;

    public TraceState() {
        tracestate = new ArrayList<>(1);
        sampleRate = null;
        entries = new LinkedHashMap<>(1);
        index = -1;
        needsUpdate = false;
    }

    public void copyFrom(TraceState other) {
        tracestate.clear();
        tracestate.addAll(other.tracestate);
        sampleRate = other.sampleRate;
        entries.clear();
        entries.putAll(other.entries);
        index = other.index;
        needsUpdate = other.needsUpdate;
    }

    public void addTextHeader(String headerValue) {
        for (String header : headerValue.split(VENDOR_ENTRIES_SEPARATOR)) {
            addSingleHeader(header);
        }
    }

    private void addSingleHeader(String header) {
        if (header.startsWith(VENDOR_PREFIX)) {
            index = tracestate.size();
            for (String entry : header.substring(3).split(ENTRY_SEPARATOR)) {
                String[] entryParts = entry.split(KEY_VALUE_SEPARATOR);
                if (entryParts.length == 2) {
                    String key = entryParts[0];
                    String value = entryParts[1];
                    if (SAMPLE_RATE_KEY.equals(key)) {
                        try {
                            double doubleValue = Double.parseDouble(value);
                            if (0 <= doubleValue && doubleValue <= 1.0) {
                                // ensure proper rounding of sample rate to minimize storage
                                // even if configuration should not allow this, any upstream value might require rounding
                                double rounded = Math.round(doubleValue * 1000d) / 1000d;

                                needsUpdate = doubleValue != rounded;
                                sampleRate = rounded;

                                entries.put(SAMPLE_RATE_KEY, sampleRate.toString());
                            }
                        } catch (NumberFormatException e) {
                            // silently ignored
                        }
                    } else {
                        entries.put(key, value);
                    }
                }
            }
        }
        tracestate.add(header);
    }

    public void setSampleRate(@Nullable Double rate) {
        if (null == sampleRate && rate != null) {
            // set first sample rate, actual value will be lazily written
            tracestate.add(null);
            index = 0;
        }
        if (rate == null) {
            entries.remove(SAMPLE_RATE_KEY);
        }
        needsUpdate = true;
        sampleRate = rate;
    }

    /**
     * @return sample rate set in tracestate header, {@literal null} if not set
     */
    @Nullable
    public Double getSampleRate() {
        return sampleRate;
    }

    /**
     * Serializes tracestate to a string representation
     *
     * @return string representation of tracestate header
     */
    @Nullable
    public String toTextHeader(int sizeLimit) {
        if (tracestate.isEmpty()) {
            return null;
        }
        if (needsUpdate) {
            if (sampleRate != null) {
                entries.put(SAMPLE_RATE_KEY, sampleRate.toString());
            }
            tracestate.set(index, getHeaderValue(entries));
        }

        String value;
        if (tracestate.size() == 1) {
            value = tracestate.get(0);
        } else {
            value = TextTracestateAppender.instance().join(tracestate, sizeLimit);
        }
        return value;
    }

    @Nullable
    private static String getHeaderValue(Map<String, String> entries) {
        if (entries.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(VENDOR_PREFIX);
        boolean isFirst = true;
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (!isFirst) {
                sb.append(ENTRY_SEPARATOR);
            }
            sb.append(entry.getKey()).append(KEY_VALUE_SEPARATOR).append(entry.getValue());
            isFirst = false;
        }
        return sb.toString();
    }

    @Nullable
    String toTextHeader() {
        return toTextHeader(Integer.MAX_VALUE);
    }

    @Override
    public void resetState() {
        tracestate.clear();
        sampleRate = null;
        entries.clear();
        index = -1;
        needsUpdate = false;
    }
}
