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
import java.util.Objects;

public class GlobalTracer implements Tracer {

    private static final GlobalTracer INSTANCE = new GlobalTracer();
    private volatile Tracer tracer = new NoopTracer(TracerState.UNINITIALIZED);

    private GlobalTracer() {
    }

    public static Tracer get() {
        return INSTANCE;
    }

    @Nullable
    public static ElasticApmTracer getTracerImpl() {
        Tracer tracer = INSTANCE.tracer;
        if (tracer instanceof ElasticApmTracer) {
            return ((ElasticApmTracer) tracer);
        }
        return null;
    }

    public static ElasticApmTracer requireTracerImpl() {
        return Objects.requireNonNull(getTracerImpl(), "Elastic APM Agent is not initialized. Make sure to remove " +
            "any classpath reference or explicit dependency of the agent. The agent should only be attached using the " +
            "documented methods and never referenced otherwise.");
    }

    public static synchronized void setNoop() {
        TracerState currentTracerState = INSTANCE.tracer.getState();
        if (currentTracerState != TracerState.UNINITIALIZED && currentTracerState != TracerState.STOPPED) {
             throw new IllegalStateException("Can't override tracer as current tracer is already running");
        }
        INSTANCE.tracer = new NoopTracer(currentTracerState);
    }

    public static synchronized void init(Tracer tracer) {
        if (!isNoop()) {
             throw new IllegalStateException("Tracer is already initialized");
        }
        INSTANCE.tracer = tracer;
    }

    public static boolean isNoop() {
        return INSTANCE.tracer instanceof NoopTracer;
    }

    @Nullable
    public Transaction startRootTransaction(@Nullable ClassLoader initiatingClassLoader) {
        return tracer.startRootTransaction(initiatingClassLoader);
    }

    @Nullable
    @Override
    public Transaction startRootTransaction(@Nullable ClassLoader initiatingClassLoader, long epochMicro) {
        return tracer.startRootTransaction(initiatingClassLoader, epochMicro);
    }

    @Nullable
    public Transaction startRootTransaction(Sampler sampler, long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        return tracer.startRootTransaction(sampler, epochMicros, initiatingClassLoader);
    }

    @Nullable
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return tracer.startChildTransaction(headerCarrier, textHeadersGetter, initiatingClassLoader);
    }

    @Nullable
    @Override
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader, long epochMicros) {
        return tracer.startChildTransaction(headerCarrier, textHeadersGetter, initiatingClassLoader, epochMicros);
    }

    @Nullable
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, Sampler sampler, long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        return tracer.startChildTransaction(headerCarrier, textHeadersGetter, sampler, epochMicros, initiatingClassLoader);
    }

    @Nullable
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, BinaryHeaderGetter<C> binaryHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return tracer.startChildTransaction(headerCarrier, binaryHeadersGetter, initiatingClassLoader);
    }

    @Nullable
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, BinaryHeaderGetter<C> binaryHeadersGetter, Sampler sampler, long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        return tracer.startChildTransaction(headerCarrier, binaryHeadersGetter, sampler, epochMicros, initiatingClassLoader);
    }

    @Nullable
    public Transaction currentTransaction() {
        return tracer.currentTransaction();
    }

    @Nullable
    @Override
    public AbstractSpan<?> getActive() {
        return tracer.getActive();
    }

    @Nullable
    @Override
    public Span getActiveSpan() {
        return tracer.getActiveSpan();
    }

    public void captureAndReportException(@Nullable Throwable e, ClassLoader initiatingClassLoader) {
        tracer.captureAndReportException(e, initiatingClassLoader);
    }

    @Nullable
    public String captureAndReportException(long epochMicros, @Nullable Throwable e, @Nullable AbstractSpan<?> parent) {
        return tracer.captureAndReportException(epochMicros, e, parent);
    }

    @Nullable
    public ErrorCapture captureException(@Nullable Throwable e, @Nullable AbstractSpan<?> parent, @Nullable ClassLoader initiatingClassLoader) {
        return tracer.captureException(e, parent, initiatingClassLoader);
    }

    @Nullable
    @Override
    public Span getActiveExitSpan() {
        return tracer.getActiveExitSpan();
    }

    @Override
    public TracerState getState() {
        return tracer.getState();
    }

    @Override
    public void overrideServiceNameForClassLoader(@Nullable ClassLoader classLoader, @Nullable String serviceName) {
        tracer.overrideServiceNameForClassLoader(classLoader, serviceName);
    }

    @Override
    public void stop() {
        tracer.stop();
    }

    @Override
    public boolean isRunning() {
        return tracer.isRunning();
    }

    @Nullable
    @Override
    public Span createExitChildSpan() {
        return tracer.createExitChildSpan();
    }
}
