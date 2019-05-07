/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.slf4j;

import co.elastic.apm.agent.cache.WeakKeySoftValueLoadingCache;
import co.elastic.apm.agent.impl.ActivationListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.logging.LoggingConfiguration;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class Slf4JMdcActivationListener implements ActivationListener {

    // the string concatenation prevents the shade plugin from relocating org.slf4j.MDC to co.elastic.apm.agent.shaded.slf4j.MDC
    // the toString prevents constant folding, which would also make the shade plugin relocate
    private static final String ORG_SLF4J_MDC = "org." + "slf4j.MDC".toString();
    private static final String TRACE_ID = "trace.id";
    private static final String SPAN_ID = "span.id";
    private static final String TRANSACTION_ID = "transaction.id";

    private final WeakKeySoftValueLoadingCache<ClassLoader, MethodHandle> mdcPutMethodHandleCache =
        new WeakKeySoftValueLoadingCache<>(new WeakKeySoftValueLoadingCache.ValueSupplier<ClassLoader, MethodHandle>() {
            @Nullable
            @Override
            public MethodHandle get(ClassLoader classLoader) {
                try {
                    return MethodHandles.lookup()
                        .findStatic(classLoader.loadClass(ORG_SLF4J_MDC), "put", MethodType.methodType(void.class, String.class, String.class));
                } catch (Exception ignore) {
                    // this class loader does not have the slf4j api
                    return null;
                }
            }
        });
    private final WeakKeySoftValueLoadingCache<ClassLoader, MethodHandle> mdcRemoveMethodHandleCache =
        new WeakKeySoftValueLoadingCache<>(new WeakKeySoftValueLoadingCache.ValueSupplier<ClassLoader, MethodHandle>() {
            @Nullable
            @Override
            public MethodHandle get(ClassLoader classLoader) {
                try {
                    return MethodHandles.lookup()
                        .findStatic(classLoader.loadClass(ORG_SLF4J_MDC), "remove", MethodType.methodType(void.class, String.class));
                } catch (Exception ignore) {
                    // this class loader does not have the slf4j api
                    return null;
                }
            }
        });
    private final LoggingConfiguration config;
    private final ElasticApmTracer tracer;

    public Slf4JMdcActivationListener(ElasticApmTracer tracer) {
        this.tracer = tracer;
        this.config = tracer.getConfig(LoggingConfiguration.class);
    }

    @Override
    public void beforeActivate(TraceContextHolder<?> context) throws Throwable {
        if (config.isLogCorrelationEnabled()) {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

            MethodHandle put = mdcPutMethodHandleCache.get(contextClassLoader);
            if (put != null) {
                TraceContext traceContext = context.getTraceContext();
                put.invokeExact(SPAN_ID, traceContext.getId().toString());
                if (tracer.getActive() == null) {
                    put.invokeExact(TRACE_ID, traceContext.getTraceId().toString());
                    put.invokeExact(TRANSACTION_ID, traceContext.getTransactionId().toString());
                }
            }
        }
    }

    @Override
    public void afterDeactivate() throws Throwable {
        if (config.isLogCorrelationEnabled()) {

            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            TraceContextHolder active = tracer.getActive();

            MethodHandle remove = mdcRemoveMethodHandleCache.get(contextClassLoader);
            if (remove != null) {
                if (active == null) {
                    remove.invokeExact(SPAN_ID);
                    remove.invokeExact(TRACE_ID);
                    remove.invokeExact(TRANSACTION_ID);
                }
            }

            if (active != null) {
                MethodHandle put = mdcPutMethodHandleCache.get(contextClassLoader);
                if (put != null) {
                    put.invokeExact(SPAN_ID, active.getTraceContext().getId().toString());
                }
            }
        }
    }
}
