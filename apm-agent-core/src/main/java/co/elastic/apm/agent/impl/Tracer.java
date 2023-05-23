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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.configuration.ServiceInfo;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.tracer.dispatch.BinaryHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.HeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;

import javax.annotation.Nullable;

public interface Tracer extends co.elastic.apm.agent.tracer.Tracer {

    @Nullable
    @Override
    Transaction startRootTransaction(@Nullable ClassLoader initiatingClassLoader);

    @Nullable
    Transaction startRootTransaction(@Nullable ClassLoader initiatingClassLoader, long epochMicro);

    /**
     * Starts a trace-root transaction with a specified sampler and start timestamp
     *
     * @param sampler               the {@link Sampler} instance which is responsible for determining the sampling decision if this is a root transaction
     * @param epochMicros           the start timestamp
     * @param initiatingClassLoader the class loader corresponding to the service which initiated the creation of the transaction.
     *                              Used to determine the service name and to load application-scoped classes like the {@link org.slf4j.MDC},
     *                              for log correlation.
     * @return a transaction that will be the root of the current trace if the agent is currently RUNNING; null otherwise
     */
    @Nullable
    Transaction startRootTransaction(Sampler sampler, long epochMicros, @Nullable ClassLoader initiatingClassLoader);

    @Override
    @Nullable
    <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader);

    @Nullable
    <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader, long epochMicros);

    /**
     * Starts a transaction as a child of the context headers obtained through the provided {@link HeaderGetter}.
     * If the created transaction cannot be started as a child transaction (for example - if no parent context header is
     * available), then it will be started as the root transaction of the trace.
     *
     * @param headerCarrier         the Object from which context headers can be obtained, typically a request or a message
     * @param textHeadersGetter     provides the trace context headers required in order to create a child transaction
     * @param sampler               the {@link Sampler} instance which is responsible for determining the sampling decision if this is a root transaction
     * @param epochMicros           the start timestamp
     * @param initiatingClassLoader the class loader corresponding to the service which initiated the creation of the transaction.
     *                              Used to determine the service name and to load application-scoped classes like the {@link org.slf4j.MDC},
     *                              for log correlation.
     * @return a transaction which is a child of the provided parent if the agent is currently RUNNING; null otherwise
     */
    @Nullable
    <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, Sampler sampler,
                                          long epochMicros, @Nullable ClassLoader initiatingClassLoader);

    @Override
    @Nullable
    <C> Transaction startChildTransaction(@Nullable C headerCarrier, BinaryHeaderGetter<C> binaryHeadersGetter, @Nullable ClassLoader initiatingClassLoader);

    /**
     * Starts a transaction as a child of the context headers obtained through the provided {@link HeaderGetter}.
     * If the created transaction cannot be started as a child transaction (for example - if no parent context header is
     * available), then it will be started as the root transaction of the trace.
     *
     * @param headerCarrier         the Object from which context headers can be obtained, typically a request or a message
     * @param binaryHeadersGetter   provides the trace context headers required in order to create a child transaction
     * @param sampler               the {@link Sampler} instance which is responsible for determining the sampling decision if this is a root transaction
     * @param epochMicros           the start timestamp
     * @param initiatingClassLoader the class loader corresponding to the service which initiated the creation of the transaction.
     *                              Used to determine the service name and to load application-scoped classes like the {@link org.slf4j.MDC},
     *                              for log correlation.
     * @return a transaction which is a child of the provided parent if the agent is currently RUNNING; null otherwise
     */
    @Nullable
    <C> Transaction startChildTransaction(@Nullable C headerCarrier, BinaryHeaderGetter<C> binaryHeadersGetter,
                                          Sampler sampler, long epochMicros, @Nullable ClassLoader initiatingClassLoader);

    @Override
    @Nullable
    Transaction currentTransaction();

    @Override
    @Nullable
    AbstractSpan<?> getActive();

    @Nullable
    Span getActiveSpan();

    /**
     * Captures an exception without providing an explicit reference to a parent {@link AbstractSpan}
     *
     * @param e                     the exception to capture
     * @param initiatingClassLoader the class
     */
    void captureAndReportException(@Nullable Throwable e, ClassLoader initiatingClassLoader);

    @Nullable
    String captureAndReportException(long epochMicros, @Nullable Throwable e, @Nullable AbstractSpan<?> parent);

    @Nullable
    ErrorCapture captureException(@Nullable Throwable e, @Nullable AbstractSpan<?> parent, @Nullable ClassLoader initiatingClassLoader);

    @Nullable
    Span getActiveExitSpan();

    TracerState getState();

    @Nullable
    ServiceInfo getServiceInfoForClassLoader(@Nullable ClassLoader classLoader);

    /**
     * Sets the service name and version for all {@link Transaction}s,
     * {@link Span}s and {@link ErrorCapture}s which are created by the service which corresponds to the provided {@link ClassLoader}.
     * <p>
     * The main use case is being able to differentiate between multiple services deployed to the same application server.
     * </p>
     *
     * @param classLoader the class loader which corresponds to a particular service
     * @param serviceInfo the service name and version for this class loader
     */
    void setServiceInfoForClassLoader(@Nullable ClassLoader classLoader, ServiceInfo serviceInfo);

    /**
     * Called when the container shuts down.
     * Cleans up thread pools and other resources.
     */
    void stop();

    @Override
    boolean isRunning();

    @Nullable
    Span createExitChildSpan();

    /**
     * An enumeration used to represent the current tracer state.
     */
    enum TracerState {
        /**
         * The agent's state before it has been started for the first time.
         */
        UNINITIALIZED,

        /**
         * Indicates that the agent is currently fully functional - tracing, monitoring and sending data to the APM server.
         */
        RUNNING,

        /**
         * The agent is mostly idle, consuming minimal resources, ready to quickly resume back to RUNNING. When the agent
         * is PAUSED, it is not tracing and not communicating with the APM server. However, classes are still instrumented
         * and threads are still alive.
         */
        PAUSED,

        /**
         * Indicates that the agent had been stopped.
         * NOTE: this state is irreversible- the agent cannot resume if it has already been stopped.
         */
        STOPPED
    }
}
