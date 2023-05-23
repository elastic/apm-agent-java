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
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.tracer.Scope;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Implementation of {@link ElasticContext} that allows to wrap an existing {@link ElasticContext} object in order to
 * integrate it with an alternate Tracing API. This is required in the following cases:
 * <ul>
 *     <li>an active Elastic span/transaction is accessed from OpenTelemetry API</li>
 *     <li>an active OpenTelemetry span created by one external plugin is accessed from another external plugin</li>
 * </ul>
 * <p>
 *     When such cases occur, the currently active {@link ElasticContext} in {@link co.elastic.apm.agent.impl.ElasticApmTracer}
 *     is replaced with an instance of this class in order to store references to the wrapper objects that allow to expose
 *     the active span to the alternate Tracing API.
 * </p>
 * <p>
 *       This replacement is transparent to the creator of the original active {@link ElasticContext}. Calling (and
 *       optionally register) the wrapper is done through {@link ElasticApmTracer#wrapActiveContextIfRequired(Class, Callable)}
 * </p>
 *
 * @param <T>
 */
public class ElasticContextWrapper<T extends ElasticContext<T>> implements ElasticContext<T> {

    /**
     * Original wrapped context
     */
    private final ElasticContext<T> context;

    /**
     * Contains other stored wrappers, one entry per type
     */
    private final Map<Class<?>, ElasticContext<?>> contextWrappers;

    public ElasticContextWrapper(int initialSize, ElasticContext<T> context) {
        this.contextWrappers = new HashMap<>(initialSize, 1.0f);
        this.context = context;
    }

    /**
     * Wraps and stores wrapper into this elastic context or returns the existing wrapper.
     *
     * @param wrapperType  wrapper type
     * @param wrapFunction wrapper function that will create wrapper if required
     * @param <C>          type of context wrapper
     * @return newly or previously created wrapper
     */
    public <C extends ElasticContext<C>> C wrapIfRequired(Class<C> wrapperType, Callable<C> wrapFunction) {
        ElasticContext<?> wrapped = contextWrappers.get(wrapperType);
        if (wrapped == null) {
            try {
                wrapped = Objects.requireNonNull(wrapFunction.call());
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }

            contextWrappers.put(wrapperType, wrapped);
        }
        return (C) wrapped;
    }

    /**
     * @return original context that was wrapped in this wrapper
     */
    public ElasticContext<T> getWrappedContext() {
        return context;
    }

    @Override
    public T activate() {
        throw activationNotSupported();
    }

    @Override
    public T deactivate() {
        throw activationNotSupported();
    }

    @Override
    public Scope activateInScope() {
        throw activationNotSupported();
    }

    @Override
    public ElasticContext<T> withActiveSpan(AbstractSpan<?> span) {
        throw activationNotSupported();
    }

    private static UnsupportedOperationException activationNotSupported() {
        // activation is not expected to happen on this wrapper but only on the wrapped context
        return new UnsupportedOperationException("activation not expected on context wrapper");
    }

    @Override
    @Nullable
    public AbstractSpan<?> getSpan() {
        return context.getSpan();
    }

    @Override
    @Nullable
    public Transaction getTransaction() {
        return context.getTransaction();
    }

}
