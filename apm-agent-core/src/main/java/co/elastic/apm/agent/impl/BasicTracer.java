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
import co.elastic.apm.agent.impl.transaction.*;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.ObjectPoolFactory;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.plugin.spi.MinimalConfiguration;
import co.elastic.apm.plugin.spi.Tracer;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.ServiceLoader;

public class BasicTracer implements SpanAwareTracer {

    private static final Logger logger = LoggerFactory.getLogger(BasicTracer.class);

    protected static final WeakMap<ClassLoader, ServiceInfo> serviceInfoByClassLoader = WeakConcurrent.buildMap();

    private final ObjectPoolFactory objectPoolFactory;
    protected final ObjectPool<Transaction> transactionPool;
    protected final ObjectPool<ErrorCapture> errorPool;
    protected final ObjectPool<Span> spanPool;
    protected final ObjectPool<TraceContext> spanLinkPool;

    protected final ThreadLocal<ActiveStack> activeStack;

    protected Sampler sampler;
    protected boolean assertionsEnabled;

    protected BasicTracer(
        int maxPooledElements,
        final int transactionMaxSpans,
        ObjectPoolFactory objectPoolFactory
    ) {
        this.objectPoolFactory = objectPoolFactory;
        this.transactionPool = objectPoolFactory.createTransactionPool(maxPooledElements, this);
        // we are assuming that we don't need as many errors as spans or transactions
        this.errorPool = objectPoolFactory.createErrorPool(maxPooledElements / 2, this);
        this.spanPool = objectPoolFactory.createSpanPool(maxPooledElements, this);
        // span links pool allows for 10X the maximum allowed span links per span
        this.spanLinkPool = objectPoolFactory.createSpanLinkPool(AbstractSpan.MAX_ALLOWED_SPAN_LINKS * 10, this);
        activeStack = new ThreadLocal<ActiveStack>() {
            @Override
            protected ActiveStack initialValue() {
                return new ActiveStack(transactionMaxSpans);
            }
        };
        // sets the assertionsEnabled flag to true if indeed enabled
        boolean assertionsEnabled = false;
        //noinspection AssertWithSideEffects
        assert assertionsEnabled = true;
        this.assertionsEnabled = assertionsEnabled;
    }

    @Override
    public ObjectPoolFactory getObjectPoolFactory() {
        return objectPoolFactory;
    }

    @Override
    @Nullable
    public Transaction startRootTransaction(@Nullable ClassLoader initiatingClassLoader) {
        return startRootTransaction(sampler, -1, initiatingClassLoader);
    }

    @Override
    @Nullable
    public Transaction startRootTransaction(@Nullable ClassLoader initiatingClassLoader, long epochMicro) {
        return startRootTransaction(sampler, epochMicro, initiatingClassLoader);
    }

    @Override
    @Nullable
    public Transaction startRootTransaction(Sampler sampler, long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        Transaction transaction = null;
        if (isRunning()) {
            transaction = createTransaction().startRoot(epochMicros, sampler);
            afterTransactionStart(initiatingClassLoader, transaction);
        }
        return transaction;
    }

    @Override
    @Nullable
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return startChildTransaction(headerCarrier, textHeadersGetter, sampler, -1, initiatingClassLoader);
    }

    @Override
    @Nullable
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader, long epochMicros) {
        return startChildTransaction(headerCarrier, textHeadersGetter, sampler, epochMicros, initiatingClassLoader);
    }

    @Override
    @Nullable
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, Sampler sampler,
                                                 long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        Transaction transaction = null;
        if (isRunning()) {
            transaction = createTransaction().start(TraceContext.<C>getFromTraceContextTextHeaders(), headerCarrier,
                textHeadersGetter, epochMicros, sampler);
            afterTransactionStart(initiatingClassLoader, transaction);
        }
        return transaction;
    }

    @Override
    @Nullable
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, BinaryHeaderGetter<C> binaryHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return startChildTransaction(headerCarrier, binaryHeadersGetter, sampler, -1, initiatingClassLoader);
    }

    @Override
    @Nullable
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, BinaryHeaderGetter<C> binaryHeadersGetter,
                                                 Sampler sampler, long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        Transaction transaction = null;
        if (isRunning()) {
            transaction = createTransaction().start(TraceContext.<C>getFromTraceContextBinaryHeaders(), headerCarrier,
                binaryHeadersGetter, epochMicros, sampler);
            afterTransactionStart(initiatingClassLoader, transaction);
        }
        return transaction;
    }

    private void afterTransactionStart(@Nullable ClassLoader initiatingClassLoader, Transaction transaction) {
        if (logger.isDebugEnabled()) {
            logger.debug("startTransaction {}", transaction);
            if (logger.isTraceEnabled()) {
                logger.trace("starting transaction at",
                    new RuntimeException("this exception is just used to record where the transaction has been started from"));
            }
        }
        final ServiceInfo serviceInfo = getServiceInfoForClassLoader(initiatingClassLoader);
        if (serviceInfo != null) {
            transaction.getTraceContext().setServiceInfo(serviceInfo.getServiceName(), serviceInfo.getServiceVersion());
        }
    }

    protected Transaction createTransaction() {
        Transaction transaction = transactionPool.createInstance();
        while (transaction.getReferenceCount() != 0) {
            logger.warn("Tried to start a transaction with a non-zero reference count {} {}", transaction.getReferenceCount(), transaction);
            transaction = transactionPool.createInstance();
        }
        return transaction;
    }

    @Override
    @Nullable
    public Transaction currentTransaction() {
        return activeStack.get().currentTransaction();
    }

    @Override
    public void endTransaction(Transaction transaction) {
        if (transaction.isNoop() || !transaction.isSampled()) {
            transaction.decrementReferences();
        }
    }

    @Override
    public void captureAndReportException(@Nullable Throwable e, ClassLoader initiatingClassLoader) {
        ErrorCapture errorCapture = captureException(System.currentTimeMillis() * 1000, e, getActive(), initiatingClassLoader);
        if (errorCapture != null) {
            errorCapture.end();
        }
    }

    @Override
    public void endSpan(Span span) {
        if (!span.isSampled() || span.isDiscarded()) {
            Transaction transaction = span.getTransaction();
            if (transaction != null) {
                transaction.captureDroppedSpan(span);
            }
            span.decrementReferences();
        }
    }

    @Override
    @Nullable
    public String captureAndReportException(long epochMicros, @Nullable Throwable e, @Nullable AbstractSpan<?> parent) {
        String id = null;
        ErrorCapture errorCapture = captureException(epochMicros, e, parent, null);
        if (errorCapture != null) {
            id = errorCapture.getTraceContext().getId().toString();
            errorCapture.end();
        }
        return id;
    }

    @Override
    @Nullable
    public ErrorCapture captureException(@Nullable Throwable e, @Nullable AbstractSpan<?> parent, @Nullable ClassLoader initiatingClassLoader) {
        return captureException(System.currentTimeMillis() * 1000, e, parent, initiatingClassLoader);
    }

    @Nullable
    private ErrorCapture captureException(long epochMicros, @Nullable Throwable e, @Nullable AbstractSpan<?> parent, @Nullable ClassLoader initiatingClassLoader) {
        if (!isRunning()) {
            return null;
        }
        // note: if we add inheritance support for exception filtering, caching would be required for performance
        if (e != null && !isIgnoredException(e)) {
            ErrorCapture error = errorPool.createInstance();
            error.withTimestamp(epochMicros);
            error.setException(e);
            Transaction currentTransaction = currentTransaction();
            if (currentTransaction != null) {
                if (currentTransaction.getNameForSerialization().length() > 0) {
                    error.setTransactionName(currentTransaction.getNameForSerialization());
                }
                error.setTransactionType(currentTransaction.getType());
                error.setTransactionSampled(currentTransaction.isSampled());
            }
            if (parent != null) {
                error.asChildOf(parent);
                // don't discard spans leading up to an error, otherwise they'd point to an invalid parent
                parent.setNonDiscardable();
            } else {
                error.getTraceContext().getId().setToRandomValue();
                ServiceInfo serviceInfo = getServiceInfoForClassLoader(initiatingClassLoader);
                if (serviceInfo != null) {
                    error.getTraceContext().setServiceInfo(serviceInfo.getServiceName(), serviceInfo.getServiceVersion());
                }
            }
            return error;
        }
        return null;
    }

    protected boolean isIgnoredException(Throwable e) {
        return true;
    }

    @Override
    @Nullable
    public AbstractSpan<?> getActive() {
        ElasticContext<?> active = currentContext();
        return active != null ? active.getSpan() : null;
    }

    /**
     * @return the currently active context, {@literal null} if there is none.
     */
    @Nullable
    public ElasticContext<?> currentContext() {
        return activeStack.get().currentContext();
    }

    @Nullable
    @Override
    public Span getActiveSpan() {
        final AbstractSpan<?> active = getActive();
        if (active instanceof Span) {
            return (Span) active;
        }
        return null;
    }

    @Nullable
    @Override
    public Span getActiveExitSpan() {
        final Span span = getActiveSpan();
        if (span != null && span.isExit()) {
            return span;
        }
        return null;
    }

    @Override
    @Nullable
    public Span createExitChildSpan() {
        AbstractSpan<?> active = getActive();
        if (active == null) {
            return null;
        }
        return active.createExitSpan();
    }

    @Override
    public void setServiceInfoForClassLoader(@Nullable ClassLoader classLoader, ServiceInfo serviceInfo) {
        // overriding the service name/version for the bootstrap class loader is not an actual use-case
        // null may also mean we don't know about the initiating class loader
        if (classLoader == null
            || !serviceInfo.hasServiceName()
            // if the service name is set explicitly, don't override it
            || hasServiceName()) {
            return;
        }

        logger.debug("Using `{}` as the service name and `{}` as the service version for class loader [{}]", serviceInfo.getServiceName(), serviceInfo.getServiceVersion(), classLoader);
        if (!serviceInfoByClassLoader.containsKey(classLoader)) {
            serviceInfoByClassLoader.putIfAbsent(classLoader, serviceInfo);
        }
    }

    protected boolean hasServiceName() {
        return false;
    }

    @Nullable
    @Override
    public ServiceInfo getServiceInfoForClassLoader(@Nullable ClassLoader classLoader) {
        if (classLoader == null) {
            return null;
        }
        return serviceInfoByClassLoader.get(classLoader);
    }

    @Override
    public TraceContext createSpanLink() {
        return spanLinkPool.createInstance();
    }

    /**
     * Starts a span with a given parent context.
     * <p>
     * This method makes it possible to start a span after the parent has already ended.
     * </p>
     *
     * @param parentContext the trace context of the parent
     * @return a new started span
     */
    public <T> Span startSpan(TraceContext.ChildContextCreator<T> childContextCreator, T parentContext) {
        return startSpan(childContextCreator, parentContext, -1);
    }

    public Span startSpan(AbstractSpan<?> parent, long epochMicros) {
        return startSpan(TraceContext.fromParent(), parent, epochMicros);
    }

    /**
     * @param parentContext the trace context of the parent
     * @param epochMicros   the start timestamp of the span in microseconds after epoch
     * @return a new started span
     * @see #startSpan(TraceContext.ChildContextCreator, Object)
     */
    public <T> Span startSpan(TraceContext.ChildContextCreator<T> childContextCreator, T parentContext, long epochMicros) {
        return createSpan().start(childContextCreator, parentContext, epochMicros);
    }

    private Span createSpan() {
        Span span = spanPool.createInstance();
        while (span.getReferenceCount() != 0) {
            logger.warn("Tried to start a span with a non-zero reference count {} {}", span.getReferenceCount(), span);
            span = spanPool.createInstance();
        }
        return span;
    }

    @Override
    public void recycle(Transaction transaction) {
        transactionPool.recycle(transaction);
    }

    @Override
    public void recycle(Span span) {
        spanPool.recycle(span);
    }

    @Override
    public void recycle(ErrorCapture error) {
        errorPool.recycle(error);
    }

    @Override
    public void recycle(TraceContext traceContext) {
        spanLinkPool.recycle(traceContext);
    }

    @Override
    public void activate(ElasticContext<?> context) {
        activeStack.get().activate(context, Collections.<ActivationListener>emptyList());
    }

    @Override
    public void deactivate(ElasticContext<?> context) {
        activeStack.get().deactivate(context, Collections.<ActivationListener>emptyList(), assertionsEnabled);
    }

    @Override
    public Scope activateInScope(final ElasticContext<?> context) {
        // already in scope
        if (currentContext() == context) {
            return Scope.NoopScope.INSTANCE;
        }
        context.activate();

        if (context instanceof Scope) {
            // we can take shortcut and avoid creating a separate object
            return (Scope) context;
        }
        return new Scope() {
            @Override
            public void close() {
                context.deactivate();
            }
        };
    }

    @Override
    public void endError(ErrorCapture errorCapture) { }

    @Override
    public void stop() { }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public TracerState getState() {
        return TracerState.RUNNING;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConfig(final Class<T> configProvider) {
        for (MinimalConfiguration configuration : ServiceLoader.load(MinimalConfiguration.class, configProvider.getClassLoader())) {
            if (configProvider.isInstance(configuration)) {
                return (T) configuration;
            }
        }
        throw new IllegalStateException();
    }

    @Nullable
    @Override
    public <C> co.elastic.apm.plugin.spi.Transaction<?> startChildTransaction(@Nullable C headerCarrier, final co.elastic.apm.plugin.spi.TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return startChildTransaction(headerCarrier, new TextHeaderGetterBridge<>(textHeadersGetter), initiatingClassLoader);
    }

    @Nullable
    @Override
    public <C> co.elastic.apm.plugin.spi.Transaction<?> startChildTransaction(@Nullable C headerCarrier, co.elastic.apm.plugin.spi.TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader, long epochMicros) {
        return startChildTransaction(headerCarrier, new TextHeaderGetterBridge<C>(textHeadersGetter), initiatingClassLoader, epochMicros);
    }

    @Nullable
    @Override
    public <C> co.elastic.apm.plugin.spi.Transaction<?> startChildTransaction(@Nullable C headerCarrier, co.elastic.apm.plugin.spi.BinaryHeaderGetter<C> binaryHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return startChildTransaction(headerCarrier, new BinaryHeaderGetterBridge<C>(binaryHeadersGetter), initiatingClassLoader);
    }

    @Nullable
    @Override
    public String captureAndReportException(long epochMicros, @Nullable Throwable e, @Nullable co.elastic.apm.plugin.spi.AbstractSpan<?> parent) {
        return captureAndReportException(epochMicros, e, (AbstractSpan<?>) parent);
    }

    @Override
    public void endSpan(co.elastic.apm.plugin.spi.Span<?> span) {
        endSpan((Span) span);
    }

    @Override
    public void endTransaction(co.elastic.apm.plugin.spi.Transaction<?> transaction) {
        endTransaction((Transaction) transaction);
    }

    @Override
    public void setServiceInfoForClassLoader(ClassLoader classLoader, co.elastic.apm.plugin.spi.ServiceInfo serviceInfo) {
        setServiceInfoForClassLoader(classLoader, serviceInfo.isMultiServiceContainer()
            ? ServiceInfo.ofMultiServiceContainer(serviceInfo.getServiceName())
            : ServiceInfo.of(serviceInfo.getServiceName(), serviceInfo.getServiceVersion()));
    }

    @Override
    public ServiceInfo autoDetectedServiceName() {
        return ServiceInfo.autoDetected();
    }

    @Nullable
    @Override
    public <T extends Tracer> T probe(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        } else {
            return null;
        }
    }

    @Override
    public <T extends Tracer> T require(Class<T> type) {
        T tracer = probe(type);
        if (tracer == null) {
            throw new IllegalStateException(this + " tracer does not support features of " + type.getName());
        }
        return tracer;
    }
}
