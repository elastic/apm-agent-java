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
package co.elastic.apm.agent.tracer.metadata;

import javax.annotation.Nullable;

public interface BodyCapture {

    /**
     * Requests that the body for this span may be captured.
     * Whether it is actually captured may depend on further details not known yet when this method is called
     * (e.g. the Content-Type header).
     */
    void markEligibleForCapturing();

    /**
     * @return true, if {@link #markEligibleForCapturing()} was called for this span.
     */
    boolean isEligibleForCapturing();
    
    /**
     * This method acts as a protection mechanism so that only one instrumentation tries to capture the body.
     * It returns true, if the calling instrumentation shall start adding body byte via {@link #append(byte)}.
     * <p>
     * For this to happen, {@link #markEligibleForCapturing()} must have been called first.
     * <p>
     * After {@link #startCapture(String, int)} has returned true once, subsequent calls will return false.
     * So for example if instrumentation A and B are active for the same span, only the first one will actually be capturing the body,
     * because {@link #startCapture(String, int)} only returns true once.
     *
     * @param charset           the charset (if available) with which the request-body is encoded.
     * @param numBytesToCapture the number of bytes to capture, to configure the limit of the internal buffer
     *
     * @return true, if the calling instrumentation should be capturing the body (by calling {@link #append(byte)}
     */
    boolean startCapture(@Nullable String charset, int numBytesToCapture);

    void append(byte b);

    void append(byte[] b, int offset, int len);

    /**
     * Checks if the limit number of bytes to capture has been reached. In this case future append calls would be a no-op.
     * If this is the case, the caller can consider releasing the reference to the span to prevent potential memory leaks.
     *
     * @return true, if the maximum number of bytes supported has already been captured
     */
    boolean isFull();
}
