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

import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Implementation of {@link ElasticContext} that allows to wrap an existing {@link ElasticContext} object, for example
 * an active span, as another {@link ElasticContext} implementation. This is required for example when two external plugins
 * have to make their spans/transactions visible to each other as their respective implementation classes are loaded
 * in distinct classloaders.
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
    private final WeakMap<Class<?>, ElasticContext<?>> contextWrappers;

    public ElasticContextWrapper(int initialSize, ElasticContext<T> context) {
        this.contextWrappers = WeakConcurrent.<Class<?>, ElasticContext<?>>weakMapBuilder()
            .withInitialCapacity(initialSize)
            .build();
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
