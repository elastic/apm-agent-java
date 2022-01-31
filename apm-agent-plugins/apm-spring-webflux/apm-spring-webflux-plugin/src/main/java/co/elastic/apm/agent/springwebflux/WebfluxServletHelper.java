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
package co.elastic.apm.agent.springwebflux;

import co.elastic.apm.agent.cache.WeakKeySoftValueLoadingCache;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.springframework.http.server.reactive.AbstractServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Helper for Servlet-related feature(s) without compile-time or run-time dependencies to the Servlet API
 */
public class WebfluxServletHelper {

    private static final Logger logger = LoggerFactory.getLogger(WebfluxServletHelper.class);

    private static final MethodHandle NOOP = MethodHandles.constant(String.class, "ClassLoader cannot load Servlets");

    private static final WeakKeySoftValueLoadingCache<Class<?>, MethodHandle> getAttributeMethodHandle = new WeakKeySoftValueLoadingCache<>(new WeakKeySoftValueLoadingCache.ValueSupplier<Class<?>, MethodHandle>() {
        @Nullable
        @Override
        public MethodHandle get(Class<?> klass) {
            try {
                return MethodHandles.lookup()
                    .findVirtual(klass, "getAttribute", MethodType.methodType(Object.class, String.class));
            } catch (Exception e) {
                logger.debug("Class {} does not implement Servlet API", klass);
                return NOOP;
            }
        }
    });

    @Nullable
    public static Transaction getServletTransaction(ServerWebExchange exchange) {
        // see ServletHttpHandlerAdapter and sub-classes for implementation details

        // While the active transaction is the one created by Servlet, it would rely on the fact that we are on the
        // same thread as the one that created the transaction, which is an implementation detail.

        Transaction transaction = null;

        ServerHttpRequest exchangeRequest = exchange.getRequest();
        if (exchangeRequest instanceof AbstractServerHttpRequest) {
            Object nativeRequest = ((AbstractServerHttpRequest) exchangeRequest).getNativeRequest();

            // note: attribute name is defined in Servlet plugin and should be kept in sync
            transaction = (Transaction) getServletAttribute(nativeRequest, "co.elastic.apm.agent.servlet.ServletApiAdvice.transaction");

        }

        return transaction;
    }

    /**
     * Get Servlet request attribute through a method handle to avoid Servlet dependency
     *
     * @param nativeRequest native request type, assumed to be a subclass of {@code javax.servlet.http.HttpServletRequest}
     * @param name          attribute name
     * @return {@literal null} if request is not a Servlet request or no attribute with provided name exists
     */
    @Nullable
    private static Object getServletAttribute(Object nativeRequest, String name) {

        MethodHandle methodHandle = getAttributeMethodHandle.get(nativeRequest.getClass());
        if (methodHandle == null || methodHandle == NOOP) {
            return null;
        }

        try {
            return methodHandle.invoke(nativeRequest, name);
        } catch (Throwable thrown) {
            logger.error("unable to get servlet attribute", thrown);
            return null;
        }
    }
}
