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

    private static final int DEFAULT_SIZE_LIMIT = 4096;

    private static final char VENDOR_SEPARATOR = ',';
    private static final char ENTRY_SEPARATOR = ';';
    private static final String VENDOR_PREFIX = "es=";
    private static final String SAMPLE_RATE_PREFIX = "s:";
    private static final String FULL_PREFIX = VENDOR_PREFIX + SAMPLE_RATE_PREFIX;

    private int sizeLimit;

    private final StringBuilder header;

    private final StringBuilder rewriteBuffer;

    private final List<CharSequence> tracestate;

    /**
     * sample rate, {@link Double#NaN} if unknown or not set
     */
    private double sampleRate;

    public TraceState() {
        sampleRate = Double.NaN;
        sizeLimit = DEFAULT_SIZE_LIMIT;
        tracestate = new ArrayList<>(1);
        rewriteBuffer = new StringBuilder();
        header = new StringBuilder(FULL_PREFIX.length());
    }

    public void copyFrom(TraceState other) {
        sampleRate = other.sampleRate;
        sizeLimit = other.sizeLimit;
        tracestate.clear();
        // copy and make sure we have the immutable variant
        for (int i = 0; i < other.tracestate.size(); i++) {
            tracestate.add(other.tracestate.get(i).toString());
        }
        rewriteBuffer.setLength(0);
        header.setLength(0);
        header.append(other.header);
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

                            // value needs to be re-written first
                            rewriteBuffer.setLength(0);
                            rewriteBuffer.append(headerValue, 0, valueStart);
                            rewriteBuffer.append(rounded);
                            rewriteBuffer.append(headerValue, valueEnd, headerValue.length());
                            // we don't minimize allocation as re-writing should be an exception
                            headerValue = rewriteBuffer.toString();
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

    /**
     * Sets value for trace state. Provided rate and string value are assumed to be correct and consistent
     *
     * @param rate       sample rate
     * @param rateString rate written as a string, used to minimize allocation
     * @throws IllegalStateException    if sample rate has already been set
     * @throws IllegalArgumentException if rate has an invalid value
     */
    public void set(double rate, String rateString) {
        if (!Double.isNaN(sampleRate)) {
            // sample rate is set either explicitly from this method (for root transactions)
            // or through upstream header, thus there is no need to change after. This allows to only
            // write/rewrite headers once
            throw new IllegalStateException("sample rate has already been set from headers");
        }
        sampleRate = rate;
        header.setLength(0);
        header.append(FULL_PREFIX);
        header.append(rateString);
        tracestate.add(header);
    }

    /**
     * @return sample rate between 0.0 and 1.0, or {@link Double#NaN} if not set
     */
    public double getSampleRate() {
        return sampleRate;
    }

    @Nullable
    public String toTextHeader() {
        if (tracestate.isEmpty()) {
            return null;
        } else {
            return TextTracestateAppender.instance().join(tracestate, sizeLimit);
        }
    }

    @Override
    public void resetState() {
        sampleRate = Double.NaN;
        sizeLimit = DEFAULT_SIZE_LIMIT;
        rewriteBuffer.setLength(0);
        tracestate.clear();
    }

    public void setSizeLimit(int limit) {
        if(!tracestate.isEmpty()) {
            throw new IllegalStateException("can't change size limit once headers have been added");
        }
        this.sizeLimit = limit;
    }
}
