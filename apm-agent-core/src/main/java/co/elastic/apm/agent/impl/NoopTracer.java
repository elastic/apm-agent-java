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

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.BinaryHeaderGetter;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.Transaction;

import javax.annotation.Nullable;

class NoopTracer implements Tracer {

    private static final String NON_INITIALIZED_ERROR_MESSAGE = "Attempting to trace using an uninitialized tracer. " +
        "Make sure to remove any classpath reference or explicit dependency of the agent. The agent should only be " +
        "attached using the documented methods and never referenced otherwise.";

    private final TracerState tracerState;

    NoopTracer(TracerState tracerState) {
        this.tracerState = tracerState;
    }

    /**
     * Throwing an error if the tracer was not yet initialized.
     * This can support future capability to stop and reset the tracer after it was initialized, but assumes that no
     * instrumentation should attempt to invoke any actual tracer operations prior to the first tracer initialization.
     */
    private void throwExceptionIfNotInitialized() {
        if (tracerState == TracerState.UNINITIALIZED) {
            throw new IllegalStateException(NON_INITIALIZED_ERROR_MESSAGE);
        }
    }

    @Nullable
    @Override
    public Transaction startRootTransaction(@Nullable ClassLoader initiatingClassLoader) {
        throwExceptionIfNotInitialized();
        return null;
    }

    @Nullable
    @Override
    public Transaction startRootTransaction(@Nullable ClassLoader initiatingClassLoader, long epochMicro) {
        throwExceptionIfNotInitialized();
        return null;
    }

    @Nullable
    @Override
    public Transaction startRootTransaction(Sampler sampler, long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        throwExceptionIfNotInitialized();
        return null;
    }

    @Nullable
    @Override
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        throwExceptionIfNotInitialized();
        return null;
    }

    @Nullable
    @Override
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader, long epochMicros) {
        throwExceptionIfNotInitialized();
        return null;
    }

    @Nullable
    @Override
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, Sampler sampler, long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        throwExceptionIfNotInitialized();
        return null;
    }

    @Nullable
    @Override
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, BinaryHeaderGetter<C> binaryHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        throwExceptionIfNotInitialized();
        return null;
    }

    @Nullable
    @Override
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, BinaryHeaderGetter<C> binaryHeadersGetter, Sampler sampler, long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        throwExceptionIfNotInitialized();
        return null;
    }

    @Nullable
    @Override
    public Transaction currentTransaction() {
        throwExceptionIfNotInitialized();
        return null;
    }

    @Nullable
    @Override
    public AbstractSpan<?> getActive() {
        throwExceptionIfNotInitialized();
        return null;
    }

    @Nullable
    @Override
    public Span getActiveSpan() {
        throwExceptionIfNotInitialized();
        return null;
    }

    @Override
    public void captureAndReportException(@Nullable Throwable e, ClassLoader initiatingClassLoader) {
        throwExceptionIfNotInitialized();
    }

    @Nullable
    @Override
    public String captureAndReportException(long epochMicros, @Nullable Throwable e, @Nullable AbstractSpan<?> parent) {
        throwExceptionIfNotInitialized();
        return null;
    }

    @Nullable
    @Override
    public ErrorCapture captureException(@Nullable Throwable e, @Nullable AbstractSpan<?> parent, @Nullable ClassLoader initiatingClassLoader) {
        throwExceptionIfNotInitialized();
        return null;
    }

    @Nullable
    @Override
    public Span getActiveExitSpan() {
        throwExceptionIfNotInitialized();
        return null;
    }

    @Override
    public TracerState getState() {
        return tracerState;
    }

    @Override
    public void overrideServiceNameForClassLoader(@Nullable ClassLoader classLoader, @Nullable String serviceName) {
        throwExceptionIfNotInitialized();
    }

    @Override
    public void stop() {
        throw new UnsupportedOperationException("Cannot stop a noop tracer");
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Nullable
    @Override
    public Span createExitChildSpan() {
        throwExceptionIfNotInitialized();
        return null;
    }
}
