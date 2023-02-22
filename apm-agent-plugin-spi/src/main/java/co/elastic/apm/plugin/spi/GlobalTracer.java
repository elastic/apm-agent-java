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
package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;
import java.util.Objects;

public class GlobalTracer implements Tracer {

    private static final GlobalTracer INSTANCE = new GlobalTracer();
    private volatile Tracer tracer = NoopTracer.INSTANCE;

    private GlobalTracer() {
    }

    public static Tracer get() {
        return INSTANCE;
    }

    @Nullable
    public static <T extends Tracer> T get(Class<T> type) {
        Tracer tracer = INSTANCE.tracer;
        if (type.isInstance(tracer)) {
            return type.cast(tracer);
        }
        return null;
    }

    public static <T extends Tracer> T require(Class<T> type) {
        return Objects.requireNonNull(get(type), "Registered tracer is not an instance of " + type.getName());
    }

    public static synchronized void init(Tracer tracer) {
        if (!isNoop()) {
            throw new IllegalStateException("Tracer is already initialized");
        }
        INSTANCE.tracer = tracer;
    }

    public static boolean isNoop() {
        return INSTANCE.tracer == NoopTracer.INSTANCE;
    }

    @Nullable
    public Transaction<?> startRootTransaction(@Nullable ClassLoader initiatingClassLoader) {
        return tracer.startRootTransaction(initiatingClassLoader);
    }

    @Nullable
    @Override
    public Transaction<?> startRootTransaction(@Nullable ClassLoader initiatingClassLoader, long epochMicro) {
        return tracer.startRootTransaction(initiatingClassLoader, epochMicro);
    }

    @Nullable
    public <C> Transaction<?> startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return tracer.startChildTransaction(headerCarrier, textHeadersGetter, initiatingClassLoader);
    }

    @Nullable
    @Override
    public <C> Transaction<?> startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader, long epochMicros) {
        return tracer.startChildTransaction(headerCarrier, textHeadersGetter, initiatingClassLoader, epochMicros);
    }

    @Nullable
    public <C> Transaction<?> startChildTransaction(@Nullable C headerCarrier, BinaryHeaderGetter<C> binaryHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return tracer.startChildTransaction(headerCarrier, binaryHeadersGetter, initiatingClassLoader);
    }

    @Nullable
    public Transaction<?> currentTransaction() {
        return tracer.currentTransaction();
    }

    @Nullable
    @Override
    public AbstractSpan<?> getActive() {
        return tracer.getActive();
    }

    @Nullable
    @Override
    public Span<?> getActiveSpan() {
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
    public Span<?> getActiveExitSpan() {
        return tracer.getActiveExitSpan();
    }

    @Nullable
    @Override
    public Span<?> createExitChildSpan() {
        return tracer.createExitChildSpan();
    }

    @Override
    public void endSpan(Span<?> span) {
        tracer.endSpan(span);
    }

    @Override
    public void endTransaction(Transaction<?> transaction) {
        tracer.endTransaction(transaction);
    }

    @Override
    public void endError(ErrorCapture errorCapture) {
        tracer.endError(errorCapture);
    }

    @Override
    public <T> T getConfig(Class<T> configuration) {
        return tracer.getConfig(configuration);
    }

    @Override
    public ObjectPoolFactory getObjectPoolFactory() {
        return tracer.getObjectPoolFactory();
    }

    @Override
    public boolean isRunning() {
        return tracer.isRunning();
    }

    @Override
    public void setServiceInfoForClassLoader(ClassLoader classLoader, ServiceInfo serviceInfo) {
        tracer.setServiceInfoForClassLoader(classLoader, serviceInfo);
    }

    @Override
    public ServiceInfo getServiceInfoForClassLoader(ClassLoader classLoader) {
        return tracer.getServiceInfoForClassLoader(classLoader);
    }

    @Override
    public ServiceInfo autoDetectedServiceName() {
        return tracer.autoDetectedServiceName();
    }
}
