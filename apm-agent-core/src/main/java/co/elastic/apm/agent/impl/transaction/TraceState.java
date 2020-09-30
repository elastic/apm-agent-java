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
import java.util.List;

class TraceState implements Recyclable {

    private static final char VENDOR_SEPARATOR = ',';
    private static final char ENTRY_SEPARATOR = ';';
    private static final String VENDOR_PREFIX = "es=";
    private static final String SAMPLE_RATE_PREFIX = "s:";

    /**
     * List of tracestate header values
     */
    private final List<String> tracestate;

    /**
     * sample rate, null if unknown or not set
     */
    private double sampleRate;

    // temp buffer used for rewriting
    private final StringBuilder tempBuffer = new StringBuilder();

    // cache to avoid rewriting same header many times when rate does not change
    private double lastWrittenRate = Double.NaN;
    @Nullable
    private String lastWrittenHeader = null;

    public TraceState() {
        tracestate = new ArrayList<>(1);
        sampleRate = Double.MIN_VALUE;
    }

    public void copyFrom(TraceState other) {
        tracestate.clear();
        tracestate.addAll(other.tracestate);
        sampleRate = other.sampleRate;
    }

    public void addTextHeader(String headerValue) {
        int elasticEntryStartIndex = headerValue.indexOf(VENDOR_PREFIX);

        if (index >= 0) {
            // parsing (and maybe fixing) current tracestate required
            int entriesStart = headerValue.indexOf(SAMPLE_RATE_PREFIX, index);
            if (entriesStart >= 0) {
                int valueStart = entriesStart + 2;
                int valueEnd = valueStart;
                if (valueEnd < headerValue.length()) {
                    char c = headerValue.charAt(valueEnd);
                    while (valueEnd < headerValue.length() && c != VENDOR_SEPARATOR && c != ENTRY_SEPARATOR) {
                        c = headerValue.charAt(valueEnd++);
                    }
                    if (valueEnd < headerValue.length()) {
                        // end due to separator char that needs to be trimmed
                        valueEnd--;
                    }
                }
                double value;
                try {
                    value = Double.parseDouble(headerValue.substring(valueStart, valueEnd));
                    if (0 <= value && value <= 1.0) {
                        // ensure proper rounding of sample rate to minimize storage
                        // even if configuration should not allow this, any upstream value might require rounding
                        double rounded = Math.round(value * 10000d) / 10000d;

                        if (rounded != value) {
                            headerValue = rewriteHeaderSampleRate(headerValue, valueStart, valueEnd, rounded);
                        }
                        sampleRate = rounded;
                    }
                } catch (NumberFormatException e) {
                    // silently ignored
                }
            }
        }
        tracestate.add(headerValue);
    }

    private String rewriteHeaderSampleRate(String originalHeader, int valueStart, int valueEnd, double sampleRate) {
        tempBuffer.setLength(0);
        tempBuffer.append(originalHeader, 0, valueStart);
        tempBuffer.append(sampleRate);
        tempBuffer.append(originalHeader, valueEnd, originalHeader.length());
        return tempBuffer.toString();
    }

    private String writeHeader(double sampleRate) {
        if (sampleRate == lastWrittenRate && lastWrittenHeader != null) {
            return lastWrittenHeader;
        }

        tempBuffer.setLength(0);
        tempBuffer.append(VENDOR_PREFIX);
        tempBuffer.append(SAMPLE_RATE_PREFIX).append(sampleRate);
        lastWrittenHeader = tempBuffer.toString();
        lastWrittenRate = sampleRate;
        return lastWrittenHeader;
    }

    /**
     * Sets sample rate if it hasn't already been set
     *
     * @param rate sample rate
     * @throws IllegalStateException if sample rate has already been set
     */
    public void setSampleRate(double rate) {
        if (sampleRate != Double.MIN_VALUE) {
            // sample rate is set either explicitly from this method (for root transactions)
            // or through upstream header, thus there is no need to change after. This allows to only
            // write/rewrite headers once
            throw new IllegalStateException("sample rate has already been set from headers");
        }
        tracestate.add(writeHeader(rate));
        sampleRate = rate;
    }

    /**
     * @return sample rate set in tracestate header, {@literal null} if not set
     */
    @Nullable
    public Double getSampleRate() {
        return Double.MIN_VALUE == sampleRate ? null : sampleRate;
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
        } else if (tracestate.size() == 1) {
            return tracestate.get(0);
        } else {
            return TextTracestateAppender.instance().join(tracestate, sizeLimit);
        }
    }

    @Nullable
    String toTextHeader() {
        return toTextHeader(Integer.MAX_VALUE);
    }

    @Override
    public void resetState() {
        tracestate.clear();
        sampleRate = Double.MIN_VALUE;
        tempBuffer.setLength(0);
    }
}
