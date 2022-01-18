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
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.configuration.converter.RoundedDoubleConverter;
import co.elastic.apm.agent.objectpool.Recyclable;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class TraceState implements Recyclable {

    private static final Logger log = LoggerFactory.getLogger(TraceState.class);

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

    public List<String> getTracestate() {
        return tracestate;
    }

    public void addTextHeader(String headerValue) {
        int vendorStart = headerValue.indexOf(VENDOR_PREFIX);

        if (vendorStart < 0) {
            // no ES entry
            tracestate.add(headerValue);
            return;
        }

        int vendorEnd = headerValue.indexOf(VENDOR_SEPARATOR, vendorStart);
        if (vendorEnd < 0) {
            vendorEnd = headerValue.length();
        }

        int sampleRateStart = headerValue.indexOf(SAMPLE_RATE_PREFIX, vendorStart);
        if (sampleRateStart < 0) {
            // no sample rate, rewrite
            log.warn("invalid header, no sample rate {}", headerValue);
            headerValue = rewriteRemoveInvalidHeader(headerValue, vendorStart, vendorEnd);
            if (headerValue.length() > 0) {
                tracestate.add(headerValue);
            }
            return;
        }

        int valueStart = sampleRateStart + 2;
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

        String headerValueString = headerValue.substring(valueStart, valueEnd);
        double value = Double.NaN;
        try {
            value = Double.parseDouble(headerValueString);
        } catch (NumberFormatException e) {
            // silently ignored
        }

        if (Double.isNaN(value) || value < 0 || value > 1) {
            log.warn("invalid sample rate header {}", headerValueString);
            headerValue = rewriteRemoveInvalidHeader(headerValue, vendorStart, vendorEnd);
        } else {
            if (!Double.isNaN(sampleRate)) {
                log.warn("sample rate already set to {}, trying to set it to {} through header will be ignored", sampleRate, value);
                headerValue = rewriteRemoveInvalidHeader(headerValue, vendorStart, vendorEnd);
            } else {
                // ensure proper rounding of sample rate to minimize storage
                // even if configuration should not allow this, any upstream value might require rounding
                double rounded = DOUBLE_CONVERTER.round(value);
                if (rounded != value) {
                    // value needs to be re-written due to rounding
                    headerValue = rewriteRoundedHeader(headerValue, valueStart, valueEnd, rounded);
                    sampleRate = rounded;
                } else {
                    sampleRate = value;
                }
            }
        }

        if (!headerValue.isEmpty()) {
            tracestate.add(headerValue);
        }

    }

    private String rewriteRoundedHeader(String fullHeader, int valueStart, int valueEnd, double rounded) {
        // we don't minimize allocation as re-writing should be an exception
        rewriteBuffer.setLength(0);
        rewriteBuffer.append(fullHeader, 0, valueStart);
        rewriteBuffer.append(DOUBLE_CONVERTER.toString(rounded));
        rewriteBuffer.append(fullHeader, valueEnd, fullHeader.length());
        return rewriteBuffer.toString();
    }

    private String rewriteRemoveInvalidHeader(String fullHeader, int start, int end) {
        rewriteBuffer.setLength(0);
        if (start > 0) {
            rewriteBuffer.append(fullHeader, 0, start);
        }
        if (end < fullHeader.length()) {
            rewriteBuffer.append(fullHeader, end + 1, fullHeader.length());
        }
        return rewriteBuffer.toString();
    }

    /**
     * Sets value for trace state. Provided rate and string value are assumed to be correct and consistent
     *
     * @param rate        sample rate
     * @param headerValue header value, as provided by a call to {@link #getHeaderValue(double)}
     * @throws IllegalStateException if sample rate has already been set
     */
    public void set(double rate, String headerValue) {
        if (!Double.isNaN(sampleRate)) {
            throw new IllegalStateException(String.format("sample rate already set to %f, trying to set it to %f", sampleRate, rate));
        }

        sampleRate = rate;
        tracestate.add(headerValue);
    }

    /**
     * Generates the header value for the provided sample rate. As this method allocates a new string on each
     * call, we should minimize the number of calls, ideally once for a given sample rate value.
     *
     * @param sampleRate sample rate
     * @return header value
     */
    public static String getHeaderValue(double sampleRate) {
        if (Double.isNaN(sampleRate) || sampleRate < 0 || sampleRate > 1) {
            throw new IllegalArgumentException("invalid sample rate " + sampleRate);
        }
        return FULL_PREFIX + DOUBLE_CONVERTER.toString(DOUBLE_CONVERTER.round(sampleRate));
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
