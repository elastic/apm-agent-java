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

public class TraceState implements Recyclable {

    private static final char VENDOR_SEPARATOR = ',';
    private static final char ENTRY_SEPARATOR = ';';
    private static final String VENDOR_PREFIX = "es=";
    private static final String SAMPLE_RATE_PREFIX = "s:";
    private static final String FULL_PREFIX = VENDOR_PREFIX + SAMPLE_RATE_PREFIX;

    /**
     * List of tracestate header values
     */
    private final List<String> tracestate;

    /**
     * sample rate, {@link Double#NaN} if unknown or not set
     */
    private double sampleRate;

    // temp buffer used for rewriting
    private final StringBuilder rewriteBuffer;

    public TraceState() {
        tracestate = new ArrayList<>(1);
        sampleRate = Double.NaN;
        rewriteBuffer = new StringBuilder();
    }

    public void copyFrom(TraceState other) {
        tracestate.clear();
        tracestate.addAll(other.tracestate);
        sampleRate = other.sampleRate;
    }

    public void addTextHeader(String headerValue) {
        int elasticEntryStartIndex = headerValue.indexOf(VENDOR_PREFIX);

        if (elasticEntryStartIndex >= 0) {
            // parsing (and maybe fixing) current tracestate required
            int entriesStart = headerValue.indexOf(SAMPLE_RATE_PREFIX, elasticEntryStartIndex);
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
        rewriteBuffer.setLength(0);
        rewriteBuffer.append(originalHeader, 0, valueStart);
        rewriteBuffer.append(sampleRate);
        rewriteBuffer.append(originalHeader, valueEnd, originalHeader.length());
        return rewriteBuffer.toString();
    }

    /**
     * Computes tracestate header value string representation for a given rate
     *
     * @param sampleRate sample rate
     * @return tracestate header text representation
     * @throws IllegalArgumentException if sample rate is invalid
     */
    public static String buildHeaderString(double sampleRate) {
        if (!isValidSampleRate(sampleRate)) {
            throw new IllegalArgumentException("invalid sample rate argument " + sampleRate);
        }
        return FULL_PREFIX + sampleRate;
    }

    private static boolean isValidSampleRate(double sampleRate) {
        return !Double.isNaN(sampleRate) && !Double.isInfinite(sampleRate) && sampleRate >= 0d && sampleRate <= 1.0d;
    }

    /**
     * Sets value for trace state. Provided rate and header value are assumed to be correct and consistent
     *
     * @param rate   sample rate
     * @param header header text value, returned from calling {@link #buildHeaderString(double)}.
     * @throws IllegalStateException    if sample rate has already been set
     * @throws IllegalArgumentException if rate has an invalid value
     */
    public void set(double rate, String header) {
        if (!Double.isNaN(sampleRate)) {
            // sample rate is set either explicitly from this method (for root transactions)
            // or through upstream header, thus there is no need to change after. This allows to only
            // write/rewrite headers once
            throw new IllegalStateException("sample rate has already been set from headers");
        }
        sampleRate = rate;
        tracestate.add(header);
    }

    /**
     * @return sample rate between 0.0 and 1.0, or {@link Double#NaN} if not set
     */
    public double getSampleRate() {
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
        } else if (tracestate.size() == 1) {
            return tracestate.get(0);
        } else {
            return TextTracestateAppender.instance().join(tracestate, sizeLimit);
        }
    }

    @Override
    public void resetState() {
        tracestate.clear();
        sampleRate = Double.NaN;
        rewriteBuffer.setLength(0);
    }
}
