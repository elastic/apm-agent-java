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

import co.elastic.apm.agent.configuration.converter.RoundedDoubleConverter;
import co.elastic.apm.agent.objectpool.Recyclable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class TraceState implements Recyclable {

    private static final int DEFAULT_SIZE_LIMIT = 4096;

    private static final RoundedDoubleConverter DOUBLE_CONVERTER = RoundedDoubleConverter.withDefaultPrecision();

    private static final char VENDOR_SEPARATOR = ',';
    private static final char ENTRY_SEPARATOR = ';';
    private static final String VENDOR_PREFIX = "es=";
    private static final String SAMPLE_RATE_PREFIX = "s:";
    private static final String FULL_PREFIX = VENDOR_PREFIX + SAMPLE_RATE_PREFIX;

    private int sizeLimit;

    private final StringBuilder rewriteBuffer;

    private final List<String> tracestate;

    /**
     * sample rate, {@link Double#NaN} if unknown or not set
     */
    private double sampleRate;

    public TraceState() {
        sampleRate = Double.NaN;
        sizeLimit = DEFAULT_SIZE_LIMIT;
        tracestate = new ArrayList<>(1);
        rewriteBuffer = new StringBuilder();
    }

    public void copyFrom(TraceState other) {
        sampleRate = other.sampleRate;
        sizeLimit = other.sizeLimit;
        tracestate.clear();
        for (int i = 0; i < other.tracestate.size(); i++) {
            //noinspection UseBulkOperation
            tracestate.add(other.tracestate.get(i));
        }
        rewriteBuffer.setLength(0);
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
                        double rounded = DOUBLE_CONVERTER.round(value);
                        if (rounded != value) {
                            // value needs to be re-written due to rounding

                            rewriteBuffer.setLength(0);
                            rewriteBuffer.append(headerValue, 0, valueStart);
                            rewriteBuffer.append(rounded);
                            rewriteBuffer.append(headerValue, valueEnd, headerValue.length());
                            // we don't minimize allocation as re-writing should be an exception
                            headerValue = rewriteBuffer.toString();
                        }
                        if (updateSampleRateCheck(rounded)) {
                            sampleRate = rounded;
                        }
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
     * @param rate        sample rate
     * @param headerValue header value, as provided by a call to {@link #getHeaderValue(double)}
     * @throws IllegalStateException if sample rate has already been set to a different value
     */
    public void set(double rate, String headerValue) {
        if (!updateSampleRateCheck(rate)) {
            // rate already set, but with identical value, thus nothing to update and we can silently ignore
            return;
        }

        sampleRate = rate;
        tracestate.add(headerValue);
    }

    /**
     * @param newRate new rater
     * @return true if sample rate needs to be updated, false if already set to identical value
     * @throws IllegalStateException if sample rate is already set with a different value
     */
    private boolean updateSampleRateCheck(double newRate) {
        if (Double.isNaN(sampleRate)) {
            return true;
        }
        if (newRate == sampleRate) {
            return false;
        }
        throw new IllegalStateException(String.format("sample rate has already been set from headers to %f, trying to set it to %f", sampleRate, newRate));
    }

    /**
     * Generates the header value for the provided sample rate. As this method allocates a new string on each
     * call, we should minimize the number of calls, ideally once for a given sample rate value.
     *
     * @param sampleRate sample rate
     * @return header value
     */
    public static String getHeaderValue(double sampleRate) {
        return FULL_PREFIX + sampleRate;
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
            return TextTracestateAppender.INSTANCE.join(tracestate, sizeLimit);
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
        if (!tracestate.isEmpty()) {
            throw new IllegalStateException("can't change size limit once headers have been added");
        }
        this.sizeLimit = limit;
    }

    /**
     * Internal appender uses a per-thread StringBuilder instance to concatenate the tracestate header.
     * This allows ot limit actual memory usage to be linear to the number of active threads which
     * is assumed to be far less than the number of active in-flight transactions.
     */
    private static class TextTracestateAppender {

        private static final TextTracestateAppender INSTANCE = new TextTracestateAppender();
        private final ThreadLocal<StringBuilder> tracestateBuffer = new ThreadLocal<StringBuilder>();

        private TextTracestateAppender() {
        }

        @Nullable
        public String join(List<String> tracestate, int tracestateSizeLimit) {
            String singleEntry = tracestate.size() != 1 ? null : tracestate.get(0);
            if (singleEntry != null && singleEntry.length() <= tracestateSizeLimit) {
                return singleEntry;
            }

            StringBuilder buffer = getTracestateBuffer();
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0, size = tracestate.size(); i < size; i++) {
                String value = tracestate.get(i);
                if (value != null) { // ignore null entries to allow removing entries without resizing collection
                    appendTracestateHeaderValue(value, buffer, tracestateSizeLimit);
                }
            }
            return buffer.length() == 0 ? null : buffer.toString();
        }

        void appendTracestateHeaderValue(String headerValue, StringBuilder tracestateBuffer, int tracestateSizeLimit) {
            int requiredLength = headerValue.length();
            boolean needsComma = tracestateBuffer.length() > 0;
            if (needsComma) {
                requiredLength++;
            }

            if (tracestateBuffer.length() + requiredLength <= tracestateSizeLimit) {
                // header fits completely
                if (needsComma) {
                    tracestateBuffer.append(',');
                }
                tracestateBuffer.append(headerValue);
            } else {
                // only part of header might be included
                //
                // When trimming due to size limit, we must include complete entries
                int endIndex = 0;
                for (int i = headerValue.length() - 1; i >= 0; i--) {
                    if (headerValue.charAt(i) == ',' && tracestateBuffer.length() + i < tracestateSizeLimit) {
                        endIndex = i;
                        break;
                    }
                }
                if (endIndex > 0) {
                    if (tracestateBuffer.length() > 0) {
                        tracestateBuffer.append(',');
                    }
                    tracestateBuffer.append(headerValue, 0, endIndex);
                }
            }

        }

        private StringBuilder getTracestateBuffer() {
            StringBuilder buffer = tracestateBuffer.get();
            if (buffer == null) {
                buffer = new StringBuilder();
                tracestateBuffer.set(buffer);
            } else {
                buffer.setLength(0);
            }
            return buffer;
        }
    }
}
