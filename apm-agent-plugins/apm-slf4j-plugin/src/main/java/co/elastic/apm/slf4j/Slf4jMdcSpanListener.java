/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.slf4j;

import co.elastic.apm.cache.WeakKeySoftValueLoadingCache;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.SpanListener;
import co.elastic.apm.impl.transaction.AbstractSpan;
import co.elastic.apm.impl.transaction.TraceContext;
import co.elastic.apm.logging.LoggingConfiguration;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class Slf4jMdcSpanListener implements SpanListener {

    // the string concatenation prevents the shade plugin from relocating org.slf4j.MDC to co.elastic.apm.shaded.slf4j.MDC
    // the toString prevents constant folding, which would also make the shade plugin relocate
    private static final String ORG_SLF4J_MDC = "org." + "slf4j.MDC".toString();
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
    @Nullable
    private LoggingConfiguration config;

    @Override
    public void init(ElasticApmTracer tracer) {
        config = tracer.getConfig(LoggingConfiguration.class);
    }

    @Override
    public void onActivate(AbstractSpan<?> span) throws Throwable {
        if (config != null && config.isLogCorrelationEnabled()) {
            MethodHandle put = mdcPutMethodHandleCache.get(Thread.currentThread().getContextClassLoader());
            TraceContext traceContext = span.getTraceContext();
            if (put != null) {
                put.invokeExact("traceId", traceContext.getTraceId().toString());
                put.invokeExact("spanId", traceContext.getId().toString());
            }
        }
    }

    @Override
    public void onDeactivate(AbstractSpan<?> span) throws Throwable {
        if (config != null && config.isLogCorrelationEnabled()) {
            MethodHandle remove = mdcRemoveMethodHandleCache.get(Thread.currentThread().getContextClassLoader());
            if (remove != null) {
                remove.invokeExact("traceId");
                remove.invokeExact("spanId");
            }
        }
    }

}
