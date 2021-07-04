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
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.dslplatform.json.JsonWriter;

import java.io.Closeable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public interface Reporter extends Closeable {

    void start();

    void report(Transaction transaction);

    void report(Span span);

    void report(ErrorCapture error);

    void report(JsonWriter jsonWriter);

    long getDropped();

    long getReported();

    /**
     * Flushes pending events and ends the HTTP request to APM server.
     *
     * @return A {@link Future} which resolves when the flush has been executed.
     * @throws IllegalStateException if the ring buffer has no available slots
     */
    Future<Void> flush();

    /**
     * Flushes pending events and ends the HTTP request to APM server.
     * <p>
     * This means that the first event that gets processed after the end-request-event will start a new HTTP request to APM Server.
     * </p>
     * <p>
     * This method is allocation-free.
     * It's guaranteed than any events that are reported in-between two invocations of this method have been processed after the second
     * invocation returns {@code true}.
     * </p>
     * <p>
     * If this method returns {@code false}, any of the following situations may have occurred:
     * </p>
     * <ul>
     *     <li>The connection to APM Server is not healthy</li>
     *     <li>The thread has been interrupted</li>
     *     <li>The flush event could not be processed within the provided timeout</li>
     *     <li>The provided timeout is zero or negative</li>
     * </ul>
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return {code true}, if the flush has been executed successfully
     */
    boolean hardFlush(long timeout, TimeUnit unit);

    /**
     * Flushes pending events but keeps the HTTP request to APM server alive.
     * <p>
     * This means that events that are reported after this method may be sent to APM Server within the same HTTP request.
     * </p>
     * <p>
     * This method is allocation-free.
     * It's guaranteed than any events that are reported in-between two invocations of this method have been processed after the second
     * invocation returns {@code true}.
     * </p>
     * <p>
     * If this method returns {@code false}, any of the following situations may have occurred:
     * </p>
     * <ul>
     *     <li>The connection to APM Server is not healthy</li>
     *     <li>The thread has been interrupted</li>
     *     <li>The flush event could not be processed within the provided timeout</li>
     *     <li>The provided timeout is zero or negative</li>
     * </ul>
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return {code true}, if the flush has been executed successfully
     */
    boolean softFlush(long timeout, TimeUnit unit);

    @Override
    void close();
}
