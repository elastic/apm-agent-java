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
package co.elastic.apm.agent.tracer;

import co.elastic.apm.agent.tracer.dispatch.BinaryHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.HeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.pooling.ObjectPoolFactory;

import javax.annotation.Nullable;
import java.util.Set;

public interface Tracer {

    boolean isRunning();

    @Nullable
    <T extends Tracer> T probe(Class<T> type);

    <T extends Tracer> T require(Class<T> type);

    <T> T getConfig(Class<T> configuration);

    ObjectPoolFactory getObjectPoolFactory();

    Set<String> getTraceHeaderNames();

    @Nullable
    AbstractSpan<?> getActive();

    @Nullable
    Transaction<?> currentTransaction();

    /**
     * Starts a trace-root transaction
     *
     * @param initiatingClassLoader the class loader corresponding to the service which initiated the creation of the transaction.
     *                              Used to determine the service name.
     * @return a transaction that will be the root of the current trace if the agent is currently RUNNING; null otherwise
     */
    @Nullable
    Transaction<?> startRootTransaction(@Nullable ClassLoader initiatingClassLoader);

    /**
     * Starts a transaction as a child of the context headers obtained through the provided {@link HeaderGetter}.
     * If the created transaction cannot be started as a child transaction (for example - if no parent context header is
     * available), then it will be started as the root transaction of the trace.
     *
     * @param headerCarrier         the Object from which context headers can be obtained, typically a request or a message
     * @param textHeadersGetter     provides the trace context headers required in order to create a child transaction
     * @param initiatingClassLoader the class loader corresponding to the service which initiated the creation of the transaction.
     *                              Used to determine the service name.
     * @return a transaction which is a child of the provided parent if the agent is currently RUNNING; null otherwise
     */
    @Nullable
    <C> Transaction<?> startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader);

    /**
     * Starts a transaction as a child of the context headers obtained through the provided {@link HeaderGetter}.
     * If the created transaction cannot be started as a child transaction (for example - if no parent context header is
     * available), then it will be started as the root transaction of the trace.
     *
     * @param headerCarrier         the Object from which context headers can be obtained, typically a request or a message
     * @param binaryHeadersGetter   provides the trace context headers required in order to create a child transaction
     * @param initiatingClassLoader the class loader corresponding to the service which initiated the creation of the transaction.
     *                              Used to determine the service name.
     * @return a transaction which is a child of the provided parent if the agent is currently RUNNING; null otherwise
     */
    @Nullable
    <C> Transaction<?> startChildTransaction(@Nullable C headerCarrier, BinaryHeaderGetter<C> binaryHeadersGetter, @Nullable ClassLoader initiatingClassLoader);
}
