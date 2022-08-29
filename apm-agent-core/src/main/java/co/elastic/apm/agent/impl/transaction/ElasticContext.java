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

import javax.annotation.Nullable;

public interface ElasticContext<T extends ElasticContext<T>> {

    /**
     * Makes the context active
     *
     * @return this
     */
    T activate();

    /**
     * Deactivates context
     *
     * @return this
     */
    T deactivate();

    /**
     * Activates context in a scope
     *
     * @return active scope that will deactivate context when closed
     */
    Scope activateInScope();

    /**
     * Adds a span as active within context. Might return a different context instance if required, for example
     * when the context implementation is immutable and thus can't be modified.
     *
     * @param span span to add to the context
     * @return context with activated span
     */
    ElasticContext<T> withActiveSpan(AbstractSpan<?> span);

    /**
     * @return the span/transaction that is associated to this context, {@literal null} if there is none
     */
    @Nullable
    AbstractSpan<?> getSpan();

    /**
     * @return transaction associated to this context, {@literal null} if there is none
     */
    @Nullable
    Transaction getTransaction();

    /**
     * Returns stored reference to a wrapper of this context previously set with {@link #storeWrapper(ElasticContext)},
     * {@literal null} if there is none. The context type argument is used for a strict type lookup and will only return
     * a non-null value if the argument is the implementation type of the wrapper, class inheritance does not apply.
     *
     * @param wrapperType context wrapper implementation type
     * @param <C>         returned context type
     * @return wrapped context object or {@literal null} if no such exists.
     */
    @Nullable
    <C extends ElasticContext<C>> C getWrapper(Class<C> wrapperType);

    /**
     * Stores context wrapper reference for later lookup with {@link #getWrapper(Class)}, the actual type of argument
     * will be used as key for storage and should thus be used for lookup.
     *
     * @param context context object
     * @param <C>     context type
     */
    <C extends ElasticContext<C>> void storeWrapper(C context);

}
