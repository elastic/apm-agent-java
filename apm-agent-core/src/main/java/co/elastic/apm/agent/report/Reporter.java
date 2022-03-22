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
     * <p>
     * This means that the first event that gets processed after the end-request-event will start a new HTTP request to APM Server.
     * </p>
     * <p>
     * This method is allocation-free.
     * It's guaranteed that events reported in-between two invocations of this method have been processed after the second
     * invocation returns {@code true}.
     * </p>
     * <p>
     * The caller may opt to atomically make a subsequent special HTTP request that tells the APM Server that it should flush data on its
     * part as well. Given the streaming nature of the communication with the APM Server, this is not possible within the "main" event
     * streaming request, which is created already before events are written to the connection.
     * </p>
     * <p>
     * If this method returns {@code false}, any of the following situations may have occurred:
     * </p>
     * <ul>
     *     <li>The connection to APM Server is not healthy</li>
     *     <li>The thread has been interrupted</li>
     *     <li>The flush event could not be processed within the provided timeout</li>
     * </ul>
     *
     * @param timeout the maximum time to wait. Negative values mean an indefinite timeout.
     * @param unit the time unit of the timeout argument
     * @param followupWithFlushRequest if {@code true}, the reporter will atomically make a subsequent HTTP request that tells APM Server
     *                                to flush as well, immediately after flushing the currently open connection
     * @return {code true}, if the flush has been executed successfully
     */
    boolean flush(long timeout, TimeUnit unit, boolean followupWithFlushRequest);

    /**
     * Same as {@link #flush(long, TimeUnit, boolean) flush(-1, NANOSECONDS, false)}
     * @see #flush(long, TimeUnit, boolean)
     */
    boolean flush();

    @Override
    void close();
}
