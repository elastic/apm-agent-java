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

/**
 * Container class for managing request body capture and associated state.
 * This class collects the body as encoded bytearray and a charset with which we'll attempt to decode the body.
 * <p>
 * A span goes through several states to perform body-capture:
 * <ul>
 *     <li>A span need to be marked as eligible for body capture. This is useful so that higher
 *     level instrumentation (E.g. Spring RestTemplate) can mark the span that they would like
 *     to capture the body without having to implement the capturing itself. Lower level instrumentations
 *     (e.g. for HTTPUrlConnection) will attempt to capture the body for the currently active span even
 *     if they didn't start a span themselves when the currently active span is marked as eligible.
 *     </li>
 *     <li>Even if a span is marked eligible, the body will only be captured if the preconditions have been checked.
 *     The preconditions check whether based on the agent configuration and the Content-Type header the body shall be captured or not.
 *     E.g. if the body capturing is disabled via the config, the preconditions will fail for every span.
 *     </li>
 *     <li>If the preconditions passed, capturing may be started via {@link #startCapture()}.
 *     Capturing will only start exactly once, {@link #startCapture()} will return false on subsequent calls.
 *     This prevents nested instrumentation being capable of capturing the body (e.g. Spring WebFlux WebClient
 *     and async Apache Http client) of capturing every byte multiple times and therefore reporting a garbage body.
 *     </li>
 * </ul>
 */
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
     * @return true, if either {@link #markPreconditionsFailed()} or {@link #markPreconditionsPassed(String, int)} have been called.
     */
    boolean havePreconditionsBeenChecked();

    /**
     * Ensures that the no body capturing will be performed for this span, e.g. because it is disabled via the agent config.
     */
    void markPreconditionsFailed();

    /**
     * Marks this span so that any capable instrumentation may start the capturing procedure via {@link #startCapture()}
     */
    void markPreconditionsPassed(@Nullable String requestCharset, int numBytesToCapture);


    /**
     * This method acts as a protection mechanism so that only one instrumentation tries to capture the body.
     * It returns true, if the calling instrumentation shall start adding body byte via {@link #append(byte)}.
     * <p>
     * For this to happen, both {@link #markEligibleForCapturing()} and {@link #markPreconditionsPassed(String, int)}
     * must have been called first.
     * <p>
     * After {@link #startCapture()} has returned true once, subsequent calls will return false.
     * So for example if instrumentation A and B are active for the same span, only the first one will actually be capturing the body,
     * because {@link #startCapture()} only returns true once.
     *
     * @return true, if the calling instrumentation should be capturing the body (by calling {@link #append(byte)}
     */
    boolean startCapture();

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
