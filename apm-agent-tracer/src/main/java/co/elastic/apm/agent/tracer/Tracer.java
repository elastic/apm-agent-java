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
import java.util.Set;
import java.util.concurrent.TimeUnit;

public interface Tracer {

    boolean isRunning();

    @Nullable
    <T extends Tracer> T probe(Class<T> type);

    <T extends Tracer> T require(Class<T> type);

    <T> T getConfig(Class<T> configuration);

    ObjectPoolFactory getObjectPoolFactory();

    <K, V extends ReferenceCounted> ReferenceCountedMap<K, V> newReferenceCountedMap();

    Set<String> getTraceHeaderNames();

    TraceState<?> currentContext();

    @Nullable
    AbstractSpan<?> getActive();

    @Nullable
    Transaction<?> currentTransaction();

    @Nullable
    ErrorCapture getActiveError();

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
     * @param headerGetter          provides the trace context headers required in order to create a child transaction
     * @param initiatingClassLoader the class loader corresponding to the service which initiated the creation of the transaction.
     *                              Used to determine the service name.
     * @return a transaction which is a child of the provided parent if the agent is currently RUNNING; null otherwise
     */
    @Nullable
    <T, C> Transaction<?> startChildTransaction(@Nullable C headerCarrier, HeaderGetter<T, C> headerGetter, @Nullable ClassLoader initiatingClassLoader);

    @Nullable
    ErrorCapture captureException(@Nullable Throwable e, @Nullable ClassLoader initiatingClassLoader);

    void reportLog(String log);

    void reportLog(byte[] log);

    @Nullable
    Service createService(String ephemeralId);

    @Nullable
    Throwable redactExceptionIfRequired(@Nullable Throwable original);

    void removeGauge(String name, Labels.Immutable labels);

    void addGauge(String name, Labels.Immutable labels, DoubleSupplier supplier);

    void submit(Runnable job);

    void schedule(Runnable job, long interval, TimeUnit timeUnit);

    void addShutdownHook(AutoCloseable hook);

    void reportMetric(JsonWriter metrics); // TODO: replace with internalized DSL writer that only accepts data.

    void flush();

    void completeMetaData(String name, String version, String id, String region);

    @Nullable
    ServiceInfo getServiceInfoForClassLoader(@Nullable ClassLoader initiatingClassLoader);

    /**
     * Sets the service name and version for all {@link co.elastic.apm.agent.tracer.Transaction}s,
     * {@link co.elastic.apm.agent.tracer.Span}s and {@link co.elastic.apm.agent.tracer.ErrorCapture}s which are created
     * by the service which corresponds to the provided {@link ClassLoader}.
     * <p>
     * The main use case is being able to differentiate between multiple services deployed to the same application server.
     * </p>
     *
     * @param classLoader the class loader which corresponds to a particular service
     * @param serviceInfo the service name and version for this class loader
     */
    void setServiceInfoForClassLoader(@Nullable ClassLoader classLoader, ServiceInfo serviceInfo);

    ServiceInfo autoDetectedServiceInfo();
}
