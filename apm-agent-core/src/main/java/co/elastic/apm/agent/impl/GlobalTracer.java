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
import co.elastic.apm.agent.util.PrivilegedActionUtils;
import co.elastic.apm.agent.util.VersionUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Objects;

public class GlobalTracer implements Tracer {

    private static final GlobalTracer INSTANCE;
    private final co.elastic.apm.tracer.api.Tracer tracer;
    private static volatile boolean classloaderCheckOk = false;

    private GlobalTracer() {
        tracer = co.elastic.apm.tracer.api.GlobalTracer.get();
    }

    static {
        checkClassloader();
        INSTANCE = new GlobalTracer();
    }

    public static Tracer get() {
        return INSTANCE;
    }

    @Nullable
    public static ElasticApmTracer getTracerImpl() {
        return get().probe(ElasticApmTracer.class);
    }

    public static ElasticApmTracer requireTracerImpl() {
        return Objects.requireNonNull(getTracerImpl(), "Registered tracer is not an instance of ElasticApmTracer");
    }

    private static void checkClassloader() {
        ClassLoader cl = PrivilegedActionUtils.getClassLoader(GlobalTracer.class);

        // agent currently loaded in the bootstrap CL, which is the current correct location
        if (cl == null) {
            return;
        }

        if (classloaderCheckOk) {
            return;
        }

        String agentLocation = PrivilegedActionUtils.getProtectionDomain(GlobalTracer.class).getCodeSource().getLocation().getFile();
        if (!agentLocation.endsWith(".jar")) {
            // agent is not packaged, thus we assume running tests
            classloaderCheckOk = true;
            return;
        }

        String premainClass = VersionUtils.getManifestEntry(new File(agentLocation), "Premain-Class");
        if (null == premainClass) {
            // packaged within a .jar, but not within an agent jar, thus we assume it's still for testing
            classloaderCheckOk = true;
            return;
        }

        if (premainClass.startsWith("co.elastic.apm.agent")) {
            // premain class will only be present when packaged as an agent jar
            classloaderCheckOk = true;
            return;
        }

        // A packaged agent class has been loaded outside of bootstrap classloader, we are not in the context of
        // unit/integration tests, that's likely a setup issue where the agent jar has been added to application
        // classpath.
        throw new IllegalStateException(String.format("Agent setup error: agent jar file \"%s\"  likely referenced in JVM or application classpath", agentLocation));

    }

    public static synchronized void setNoop() {
        if (co.elastic.apm.tracer.api.GlobalTracer.isNoop()) {
            return;
        }
        TracerState currentTracerState = INSTANCE.tracer.require(Tracer.class).getState();
        if (currentTracerState != TracerState.UNINITIALIZED && currentTracerState != TracerState.STOPPED) {
            throw new IllegalStateException("Can't override tracer as current tracer is already running");
        }
        co.elastic.apm.tracer.api.GlobalTracer.reset();
    }

    public static synchronized void init(Tracer tracer) {
        if (!isNoop()) {
            throw new IllegalStateException("Tracer is already initialized");
        }
        co.elastic.apm.tracer.api.GlobalTracer.init(tracer);
    }

    public static boolean isNoop() {
        return co.elastic.apm.tracer.api.GlobalTracer.isNoop();
    }

    @Nullable
    public Transaction startRootTransaction(@Nullable ClassLoader initiatingClassLoader) {
        return tracer.require(Tracer.class).startRootTransaction(initiatingClassLoader);
    }

    @Nullable
    @Override
    public Transaction startRootTransaction(@Nullable ClassLoader initiatingClassLoader, long epochMicro) {
        return tracer.require(Tracer.class).startRootTransaction(initiatingClassLoader, epochMicro);
    }

    @Nullable
    public Transaction startRootTransaction(Sampler sampler, long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        return tracer.require(Tracer.class).startRootTransaction(sampler, epochMicros, initiatingClassLoader);
    }

    @Nullable
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return tracer.require(Tracer.class).startChildTransaction(headerCarrier, textHeadersGetter, initiatingClassLoader);
    }

    @Nullable
    @Override
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader, long epochMicros) {
        return tracer.require(Tracer.class).startChildTransaction(headerCarrier, textHeadersGetter, initiatingClassLoader, epochMicros);
    }

    @Nullable
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, Sampler sampler, long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        return tracer.require(Tracer.class).startChildTransaction(headerCarrier, textHeadersGetter, sampler, epochMicros, initiatingClassLoader);
    }

    @Nullable
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, BinaryHeaderGetter<C> binaryHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return tracer.require(Tracer.class).startChildTransaction(headerCarrier, binaryHeadersGetter, initiatingClassLoader);
    }

    @Nullable
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, BinaryHeaderGetter<C> binaryHeadersGetter, Sampler sampler, long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        return tracer.require(Tracer.class).startChildTransaction(headerCarrier, binaryHeadersGetter, sampler, epochMicros, initiatingClassLoader);
    }

    @Nullable
    public Transaction currentTransaction() {
        return tracer.require(Tracer.class).currentTransaction();
    }

    @Nullable
    @Override
    public AbstractSpan<?> getActive() {
        return tracer.require(Tracer.class).getActive();
    }

    @Nullable
    @Override
    public Span getActiveSpan() {
        return tracer.require(Tracer.class).getActiveSpan();
    }

    public void captureAndReportException(@Nullable Throwable e, ClassLoader initiatingClassLoader) {
        tracer.require(Tracer.class).captureAndReportException(e, initiatingClassLoader);
    }

    @Nullable
    public String captureAndReportException(long epochMicros, @Nullable Throwable e, @Nullable AbstractSpan<?> parent) {
        return tracer.require(Tracer.class).captureAndReportException(epochMicros, e, parent);
    }

    @Nullable
    public ErrorCapture captureException(@Nullable Throwable e, @Nullable AbstractSpan<?> parent, @Nullable ClassLoader initiatingClassLoader) {
        return tracer.require(Tracer.class).captureException(e, parent, initiatingClassLoader);
    }

    @Nullable
    @Override
    public Span getActiveExitSpan() {
        return tracer.require(Tracer.class).getActiveExitSpan();
    }

    @Override
    public TracerState getState() {
        if (isNoop()) {
            return TracerState.UNINITIALIZED;
        }
        return tracer.require(Tracer.class).getState();
    }

    @Nullable
    @Override
    public ServiceInfo getServiceInfoForClassLoader(@Nullable ClassLoader classLoader) {
        return tracer.require(Tracer.class).getServiceInfoForClassLoader(classLoader);
    }

    @Override
    public void setServiceInfoForClassLoader(@Nullable ClassLoader classLoader, ServiceInfo serviceInfo) {
        tracer.require(Tracer.class).setServiceInfoForClassLoader(classLoader, serviceInfo);
    }

    @Override
    public void stop() {
        tracer.require(Tracer.class).stop();
    }

    @Override
    public boolean isRunning() {
        return tracer.require(Tracer.class).isRunning();
    }

    @Nullable
    @Override
    public Span createExitChildSpan() {
        return tracer.require(Tracer.class).createExitChildSpan();
    }

    @Override
    public void recycle(Transaction transaction) {
        tracer.require(Tracer.class).recycle(transaction);
    }

    @Override
    public void endSpan(Span span) {
        tracer.require(Tracer.class).endSpan(span);
    }

    @Override
    public void endTransaction(Transaction transaction) {
        tracer.require(Tracer.class).endTransaction(transaction);
    }

    @Override
    public void endError(ErrorCapture errorCapture) {
        tracer.require(Tracer.class).endError(errorCapture);
    }

    @Override
    public <T> T getConfig(Class<T> configuration) {
        return tracer.require(Tracer.class).getConfig(configuration);
    }

    @Override
    public ObjectPoolFactory getObjectPoolFactory() {
        return tracer.require(Tracer.class).getObjectPoolFactory();
    }

    @Override
    public void recycle(ErrorCapture errorCapture) {
        tracer.require(Tracer.class).recycle(errorCapture);
    }

    @Nullable
    @Override
    public <C> co.elastic.apm.tracer.api.Transaction<?> startChildTransaction(@Nullable C headerCarrier, co.elastic.apm.tracer.api.dispatch.TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return tracer.require(Tracer.class).startChildTransaction(headerCarrier, textHeadersGetter, initiatingClassLoader);
    }

    @Nullable
    @Override
    public <C> co.elastic.apm.tracer.api.Transaction<?> startChildTransaction(@Nullable C headerCarrier, co.elastic.apm.tracer.api.dispatch.BinaryHeaderGetter<C> binaryHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return tracer.require(Tracer.class).startChildTransaction(headerCarrier, binaryHeadersGetter, initiatingClassLoader);
    }

    @Override
    public void setServiceInfoForClassLoader(ClassLoader classLoader, co.elastic.apm.tracer.api.service.ServiceInfo serviceInfo) {
        tracer.require(Tracer.class).require(GlobalTracer.class).setServiceInfoForClassLoader(classLoader, serviceInfo);
    }

    @Override
    public ServiceInfo autoDetectedServiceName() {
        return tracer.require(Tracer.class).autoDetectedServiceName();
    }

    @Nullable
    @Override
    public <T extends co.elastic.apm.tracer.api.Tracer> T probe(Class<T> type) {
        return tracer.probe(type);
    }

    @Override
    public <T extends co.elastic.apm.tracer.api.Tracer> T require(Class<T> type) {
        return tracer.require(type);
    }
}
