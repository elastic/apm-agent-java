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
import co.elastic.apm.agent.tracer.metrics.DoubleSupplier;
import co.elastic.apm.agent.tracer.metrics.Labels;
import co.elastic.apm.agent.tracer.pooling.ObjectPoolFactory;
import co.elastic.apm.agent.tracer.reference.ReferenceCounted;
import co.elastic.apm.agent.tracer.reference.ReferenceCountedMap;
import co.elastic.apm.agent.tracer.service.Service;
import co.elastic.apm.agent.tracer.service.ServiceInfo;
import com.dslplatform.json.JsonWriter;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

class NoopTracer implements Tracer {

    static final Tracer INSTANCE = new NoopTracer();

    private NoopTracer() {
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Nullable
    @Override
    public <T extends Tracer> T probe(Class<T> type) {
        return null;
    }

    @Override
    public <T extends Tracer> T require(Class<T> type) {
        throw new IllegalStateException();
    }

    @Override
    public <T> T getConfig(Class<T> configuration) {
        throw new IllegalStateException();
    }

    @Override
    public ObjectPoolFactory getObjectPoolFactory() {
        throw new IllegalStateException();
    }

    @Override
    public <K, V extends ReferenceCounted> ReferenceCountedMap<K, V> newReferenceCountedMap() {
        throw new IllegalStateException();
    }

    @Override
    public Set<String> getTraceHeaderNames() {
        return Collections.<String>emptySet();
    }

    @Override
    public TraceState<?> currentContext() {
        return NoopTraceState.INSTANCE;
    }

    @Nullable
    @Override
    public AbstractSpan<?> getActive() {
        return null;
    }

    @Nullable
    @Override
    public Transaction<?> currentTransaction() {
        return null;
    }

    @Nullable
    @Override
    public ErrorCapture getActiveError() {
        return null;
    }

    @Nullable
    @Override
    public Transaction<?> startRootTransaction(@Nullable ClassLoader initiatingClassLoader) {
        return null;
    }

    @Nullable
    @Override
    public <T, C> Transaction<?> startChildTransaction(@Nullable C headerCarrier, HeaderGetter<T, C> headerGetter, @Nullable ClassLoader initiatingClassLoader) {
        return null;
    }

    @Nullable
    @Override
    public ErrorCapture captureException(@Nullable Throwable e, @Nullable ClassLoader initiatingClassLoader) {
        return null;
    }

    @Override
    public void reportLog(String log) {
    }

    @Override
    public void reportLog(byte[] log) {
    }

    @Override
    @Nullable
    public Service createService(String ephemeralId) {
        return null;
    }

    @Nullable
    @Override
    public Throwable redactExceptionIfRequired(@Nullable Throwable original) {
        return original;
    }

    @Override
    public void flush() {
    }

    @Override
    public void completeMetaData(String name, String version, String id, String region) {
    }

    @Override
    public void addGauge(String name, Labels.Immutable labels, DoubleSupplier supplier) {
    }

    @Override
    public void removeGauge(String name, Labels.Immutable labels) {
    }

    @Override
    public void submit(Runnable job) {
    }

    @Override
    public void schedule(Runnable job, long interval, TimeUnit timeUnit) {
    }

    @Override
    public void addShutdownHook(AutoCloseable hook) {
    }

    @Override
    public void reportMetric(JsonWriter metrics) {
    }

    @Nullable
    @Override
    public ServiceInfo getServiceInfoForClassLoader(@Nullable ClassLoader initiatingClassLoader) {
        return null;
    }

    @Override
    public void setServiceInfoForClassLoader(@Nullable ClassLoader classLoader, ServiceInfo serviceInfo) {
    }

    @Override
    public ServiceInfo autoDetectedServiceInfo() {
        return ServiceInfo.empty();
    }
}
