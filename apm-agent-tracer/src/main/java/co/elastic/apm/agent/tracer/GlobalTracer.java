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

import co.elastic.apm.agent.tracer.dispatch.HeaderGetter;
import co.elastic.apm.agent.tracer.pooling.ObjectPoolFactory;
import co.elastic.apm.agent.tracer.reference.ReferenceCounted;
import co.elastic.apm.agent.tracer.reference.ReferenceCountedMap;
import co.elastic.apm.agent.tracer.service.Service;

import javax.annotation.Nullable;
import java.util.Set;

public class GlobalTracer implements Tracer {

    private static final GlobalTracer INSTANCE = new GlobalTracer();
    private volatile Tracer tracer = NoopTracer.INSTANCE;

    private GlobalTracer() {
    }

    public static Tracer get() {
        return INSTANCE;
    }

    public static synchronized void setNoop() {
        boolean running = INSTANCE.tracer.isRunning();
        if (running) {
            throw new IllegalStateException("Can't override tracer as current tracer is already running");
        }
        INSTANCE.tracer = NoopTracer.INSTANCE;
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

    @Override
    public boolean isRunning() {
        return tracer.isRunning();
    }

    @Nullable
    @Override
    public <T extends Tracer> T probe(Class<T> type) {
        return tracer.probe(type);
    }

    @Override
    public <T extends Tracer> T require(Class<T> type) {
        return tracer.require(type);
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
    public <K, V extends ReferenceCounted> ReferenceCountedMap<K, V> newReferenceCountedMap() {
        return tracer.newReferenceCountedMap();
    }

    @Override
    public Set<String> getTraceHeaderNames() {
        return tracer.getTraceHeaderNames();
    }

    @Override
    public ElasticContext<?> currentContext() {
        return tracer.currentContext();
    }

    @Nullable
    @Override
    public AbstractSpan<?> getActive() {
        return tracer.getActive();
    }

    @Nullable
    @Override
    public Transaction<?> currentTransaction() {
        return tracer.currentTransaction();
    }

    @Nullable
    @Override
    public ErrorCapture getActiveError() {
        return tracer.getActiveError();
    }

    @Nullable
    @Override
    public Transaction<?> startRootTransaction(@Nullable ClassLoader initiatingClassLoader) {
        return tracer.startRootTransaction(initiatingClassLoader);
    }

    @Nullable
    @Override
    public <T, C> Transaction<?> startChildTransaction(@Nullable C headerCarrier, HeaderGetter<T, C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return tracer.startChildTransaction(headerCarrier, textHeadersGetter, initiatingClassLoader);
    }

    @Nullable
    @Override
    public ErrorCapture captureException(@Nullable Throwable e, @Nullable ClassLoader initiatingClassLoader) {
        return tracer.captureException(e, initiatingClassLoader);
    }

    @Override
    public void reportLog(String log) {
        tracer.reportLog(log);
    }

    @Override
    public void reportLog(byte[] log) {
        tracer.reportLog(log);
    }

    @Nullable
    @Override
    public Service createService(String ephemeralId) {
        return tracer.createService(ephemeralId);
    }
}
