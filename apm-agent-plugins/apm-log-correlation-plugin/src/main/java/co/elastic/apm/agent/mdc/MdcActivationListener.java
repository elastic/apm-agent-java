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
package co.elastic.apm.agent.mdc;

import co.elastic.apm.agent.cache.WeakKeySoftValueLoadingCache;
import co.elastic.apm.agent.impl.ActivationListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class MdcActivationListener implements ActivationListener {

    private static final String SLF4J_MDC = "org.slf4j.MDC";

    private static final String LOG4J_MDC = "org.apache.log4j.MDC";

    private static final String LOG4J2_MDC = "org.apache.logging.log4j.ThreadContext";

    private static final String JBOSS_LOGGING_MDC = "org.jboss.logging.MDC";

    private static final String TRACE_ID = "trace.id";
    private static final String TRANSACTION_ID = "transaction.id";
    private static final String ERROR_ID = "error.id";
    private static final Logger logger = LoggerFactory.getLogger(MdcActivationListener.class);

    // Never invoked- only used for caching ClassLoaders that can't load the MDC/ThreadContext class
    private static final MethodHandle NOOP = MethodHandles.constant(String.class, "ClassLoader cannot load MDC/ThreadContext");

    private final WeakKeySoftValueLoadingCache<ClassLoader, MethodHandle>[] mdcPutMethodHandleCaches = new WeakKeySoftValueLoadingCache[]{
        new WeakKeySoftValueLoadingCache<>(new WeakKeySoftValueLoadingCache.ValueSupplier<ClassLoader, MethodHandle>() {
            @Nullable
            @Override
            public MethodHandle get(ClassLoader classLoader) {
                try {
                    return MethodHandles.lookup()
                        .findStatic(classLoader.loadClass(SLF4J_MDC), "put", MethodType.methodType(void.class, String.class, String.class));
                } catch (Exception e) {
                    logger.debug("Class loader " + classLoader + " cannot load slf4j API", e);
                    return NOOP;
                }
            }
        }),
        new WeakKeySoftValueLoadingCache<>(new WeakKeySoftValueLoadingCache.ValueSupplier<ClassLoader, MethodHandle>() {
            @Nullable
            @Override
            public MethodHandle get(ClassLoader classLoader) {
                try {
                    return MethodHandles.lookup()
                        .findStatic(classLoader.loadClass(LOG4J_MDC), "put", MethodType.methodType(void.class, String.class, Object.class));
                } catch (Exception e) {
                    logger.debug("Class loader " + classLoader + " cannot load log4j API", e);
                    return NOOP;
                }
            }
        }),
        new WeakKeySoftValueLoadingCache<>(new WeakKeySoftValueLoadingCache.ValueSupplier<ClassLoader, MethodHandle>() {
            @Nullable
            @Override
            public MethodHandle get(ClassLoader classLoader) {
                try {
                    return MethodHandles.lookup()
                        .findStatic(classLoader.loadClass(LOG4J2_MDC), "put", MethodType.methodType(void.class, String.class, String.class));
                } catch (Exception e) {
                    logger.debug("Class loader " + classLoader + " cannot load log4j2 API", e);
                    return NOOP;
                }
            }
        }),
        new WeakKeySoftValueLoadingCache<>(new WeakKeySoftValueLoadingCache.ValueSupplier<ClassLoader, MethodHandle>() {
            @Nullable
            @Override
            public MethodHandle get(ClassLoader classLoader) {
                try {
                    return MethodHandles.lookup()
                        .findStatic(classLoader.loadClass(JBOSS_LOGGING_MDC), "put", MethodType.methodType(Object.class, String.class, Object.class));
                } catch (Exception e) {
                    logger.debug("Class loader " + classLoader + " cannot load JBoss Logging API", e);
                    return NOOP;
                }
            }
        })
    };

    private final WeakKeySoftValueLoadingCache<ClassLoader, MethodHandle>[] mdcRemoveMethodHandleCaches = new WeakKeySoftValueLoadingCache[]{
        new WeakKeySoftValueLoadingCache<>(new WeakKeySoftValueLoadingCache.ValueSupplier<ClassLoader, MethodHandle>() {
            @Nullable
            @Override
            public MethodHandle get(ClassLoader classLoader) {
                try {
                    return MethodHandles.lookup()
                        .findStatic(classLoader.loadClass(SLF4J_MDC), "remove", MethodType.methodType(void.class, String.class));
                } catch (Exception ignore) {
                    // No need to log - logged already when populated the put cache
                    return NOOP;
                }
            }
        }),
        new WeakKeySoftValueLoadingCache<>(new WeakKeySoftValueLoadingCache.ValueSupplier<ClassLoader, MethodHandle>() {
            @Nullable
            @Override
            public MethodHandle get(ClassLoader classLoader) {
                try {
                    return MethodHandles.lookup()
                        .findStatic(classLoader.loadClass(LOG4J_MDC), "remove", MethodType.methodType(void.class, String.class));
                } catch (Exception ignore) {
                    // No need to log - logged already when populated the put cache
                    return NOOP;
                }
            }
        }),
        new WeakKeySoftValueLoadingCache<>(new WeakKeySoftValueLoadingCache.ValueSupplier<ClassLoader, MethodHandle>() {
            @Nullable
            @Override
            public MethodHandle get(ClassLoader classLoader) {
                try {
                    return MethodHandles.lookup()
                        .findStatic(classLoader.loadClass(LOG4J2_MDC), "remove", MethodType.methodType(void.class, String.class));
                } catch (Exception ignore) {
                    // No need to log - logged already when populated the put cache
                    return NOOP;
                }
            }
        }),
        new WeakKeySoftValueLoadingCache<>(new WeakKeySoftValueLoadingCache.ValueSupplier<ClassLoader, MethodHandle>() {
            @Nullable
            @Override
            public MethodHandle get(ClassLoader classLoader) {
                try {
                    return MethodHandles.lookup()
                        .findStatic(classLoader.loadClass(JBOSS_LOGGING_MDC), "remove", MethodType.methodType(void.class, String.class));
                } catch (Exception ignore) {
                    // No need to log - logged already when populated the put cache
                    return NOOP;
                }
            }
        })
    };
    private final LoggingConfiguration loggingConfiguration;
    private final ElasticApmTracer tracer;

    public MdcActivationListener(ElasticApmTracer tracer) {
        this.tracer = tracer;
        this.loggingConfiguration = tracer.getConfig(LoggingConfiguration.class);
    }

    @Override
    public void beforeActivate(AbstractSpan<?> span) throws Throwable {
        before(span.getTraceContext(), false);
    }

    @Override
    public void beforeActivate(ErrorCapture error) throws Throwable {
        before(error.getTraceContext(), true);
    }

    public void before(TraceContext traceContext, boolean isError) throws Throwable {
        if (loggingConfiguration.isLogCorrelationEnabled() && tracer.isRunning()) {
            for (WeakKeySoftValueLoadingCache<ClassLoader, MethodHandle> mdcPutMethodHandleCache : mdcPutMethodHandleCaches) {
                MethodHandle put = mdcPutMethodHandleCache.get(getApplicationClassLoader(traceContext));
                if (put != null && put != NOOP) {
                    if (isError) {
                        put.invoke(ERROR_ID, traceContext.getId().toString());
                    } else if (tracer.getActive() == null) {
                        put.invoke(TRACE_ID, traceContext.getTraceId().toString());
                        put.invoke(TRANSACTION_ID, traceContext.getTransactionId().toString());
                    }
                }
            }
        }
    }

    @Override
    public void afterDeactivate(AbstractSpan<?> deactivatedSpan) throws Throwable {
        after(deactivatedSpan.getTraceContext(), false);
    }

    @Override
    public void afterDeactivate(ErrorCapture deactivatedError) throws Throwable {
        after(deactivatedError.getTraceContext(), true);
    }

    public void after(TraceContext deactivatedContext, boolean isError) throws Throwable {
        if (loggingConfiguration.isLogCorrelationEnabled()) {
            for (WeakKeySoftValueLoadingCache<ClassLoader, MethodHandle> mdcRemoveMethodHandleCache : mdcRemoveMethodHandleCaches) {
                MethodHandle remove = mdcRemoveMethodHandleCache.get(getApplicationClassLoader(deactivatedContext));
                if (remove != null && remove != NOOP) {
                    if (isError) {
                        remove.invokeExact(ERROR_ID);
                    } else if (tracer.getActive() == null) {
                        remove.invokeExact(TRACE_ID);
                        remove.invokeExact(TRANSACTION_ID);
                    }
                }
            }
        }
    }

    /**
     * Looks up the class loader which corresponds to the application the current transaction belongs to.
     * @param context
     * @return
     */
    private ClassLoader getApplicationClassLoader(TraceContext context) {
        ClassLoader applicationClassLoader = context.getApplicationClassLoader();
        if (applicationClassLoader != null) {
            return applicationClassLoader;
        } else {
            return getFallbackClassLoader();
        }
    }
    private ClassLoader getFallbackClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader =  ClassLoader.getSystemClassLoader();
        }
        return classLoader;
    }

}
