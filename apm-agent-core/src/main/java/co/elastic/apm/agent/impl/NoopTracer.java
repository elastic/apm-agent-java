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
import co.elastic.apm.agent.impl.transaction.BinaryHeaderGetter;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.objectpool.ObjectPoolFactory;

import javax.annotation.Nullable;

class NoopTracer implements Tracer {

    static final Tracer INSTANCE = new NoopTracer();

    private NoopTracer() {
    }

    @Nullable
    @Override
    public Transaction startRootTransaction(@Nullable ClassLoader initiatingClassLoader) {
        return null;
    }

    @Nullable
    @Override
    public Transaction startRootTransaction(@Nullable ClassLoader initiatingClassLoader, long epochMicro) {
        return null;
    }

    @Nullable
    @Override
    public Transaction startRootTransaction(Sampler sampler, long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        return null;
    }

    @Nullable
    @Override
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return null;
    }

    @Nullable
    @Override
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader, long epochMicros) {
        return null;
    }

    @Nullable
    @Override
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, Sampler sampler, long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        return null;
    }

    @Nullable
    @Override
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, BinaryHeaderGetter<C> binaryHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return null;
    }

    @Nullable
    @Override
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, BinaryHeaderGetter<C> binaryHeadersGetter, Sampler sampler, long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        return null;
    }

    @Nullable
    @Override
    public Transaction currentTransaction() {
        return null;
    }

    @Nullable
    @Override
    public AbstractSpan<?> getActive() {
        return null;
    }

    @Nullable
    @Override
    public Span getActiveSpan() {
        return null;
    }

    @Override
    public void captureAndReportException(@Nullable Throwable e, ClassLoader initiatingClassLoader) {

    }

    @Nullable
    @Override
    public String captureAndReportException(long epochMicros, @Nullable Throwable e, @Nullable AbstractSpan<?> parent) {
        return null;
    }

    @Nullable
    @Override
    public ErrorCapture captureException(@Nullable Throwable e, @Nullable AbstractSpan<?> parent, @Nullable ClassLoader initiatingClassLoader) {
        return null;
    }

    @Nullable
    @Override
    public Span getActiveExitSpan() {
        return null;
    }

    @Override
    public TracerState getState() {
        return TracerState.UNINITIALIZED;
    }

    @Nullable
    @Override
    public ServiceInfo getServiceInfoForClassLoader(@Nullable ClassLoader classLoader) {
        return null;
    }

    @Override
    public void setServiceInfoForClassLoader(@Nullable ClassLoader classLoader, ServiceInfo serviceInfo) {
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Nullable
    @Override
    public Span createExitChildSpan() {
        return null;
    }

    @Override
    public void recycle(Transaction transaction) { }

    @Override
    public void recycle(ErrorCapture errorCapture) { }

    @Override
    public void endSpan(Span span) { }

    @Override
    public void endTransaction(Transaction transaction) { }

    @Override
    public void endError(ErrorCapture errorCapture) { }

    @Override
    public <T> T getConfig(Class<T> configuration) {
        return null;
    }

    @Override
    public ObjectPoolFactory getObjectPoolFactory() {
        return null;
    }

    @Nullable
    @Override
    public <C> co.elastic.apm.plugin.spi.Transaction<?> startChildTransaction(@Nullable C headerCarrier, co.elastic.apm.plugin.spi.TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return null;
    }

    @Nullable
    @Override
    public <C> co.elastic.apm.plugin.spi.Transaction<?> startChildTransaction(@Nullable C headerCarrier, co.elastic.apm.plugin.spi.TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader, long epochMicros) {
        return null;
    }

    @Nullable
    @Override
    public <C> co.elastic.apm.plugin.spi.Transaction<?> startChildTransaction(@Nullable C headerCarrier, co.elastic.apm.plugin.spi.BinaryHeaderGetter<C> binaryHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return null;
    }

    @Nullable
    @Override
    public String captureAndReportException(long epochMicros, @Nullable Throwable e, @Nullable co.elastic.apm.plugin.spi.AbstractSpan<?> parent) {
        return null;
    }

    @Override
    public void endSpan(co.elastic.apm.plugin.spi.Span<?> span) {
    }

    @Override
    public void endTransaction(co.elastic.apm.plugin.spi.Transaction<?> transaction) {
    }

    @Override
    public void setServiceInfoForClassLoader(ClassLoader classLoader, co.elastic.apm.plugin.spi.ServiceInfo serviceInfo) {
    }

    @Override
    public ServiceInfo autoDetectedServiceName() {
        return null;
    }

    @Nullable
    @Override
    public <T extends co.elastic.apm.plugin.spi.Tracer> T probe(Class<T> type) {
        return null;
    }

    @Override
    public <T extends co.elastic.apm.plugin.spi.Tracer> T require(Class<T> type) {
        return null;
    }
}
