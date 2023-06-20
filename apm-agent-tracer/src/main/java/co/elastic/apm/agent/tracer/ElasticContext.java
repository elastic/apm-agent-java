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

import co.elastic.apm.agent.tracer.dispatch.BinaryHeaderSetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;
import co.elastic.apm.agent.tracer.reference.ReferenceCounted;

import javax.annotation.Nullable;

public interface ElasticContext<T extends ElasticContext<T>> extends ReferenceCounted {

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
     * @return the span/transaction that is associated to this context, {@literal null} if there is none
     */
    @Nullable
    AbstractSpan<?> getSpan();

    /**
     * @return the transaction that is associated to this context, {@literal null} if there is none
     */
    @Nullable
    Transaction<?> getTransaction();

    /**
     * If a context is empty, it does not need to be propagated (neither within the process, nor via external calls).
     *
     * @return true, if this context contains nothing (neither a span, nor baggage nor anything else for wrapped contexts).
     */
    boolean isEmpty();

    /**
     * Propagates this context onto the given carrier. This includes both trace context and baggage.
     *
     * @param carrier      the binary headers carrier
     * @param headerSetter a setter implementing the actual addition of headers to the headers carrier
     * @param <C>          the header carrier type, for example - a Kafka record
     * @return true if Trace Context headers were set; false otherwise
     */
    <C> boolean propagateContext(C carrier, BinaryHeaderSetter<C> headerSetter);

    /**
     * Propagates this context onto the given carrier. This includes both trace context and baggage.
     * This method ensures that if trace-context headers are already present, they will not be overridden.
     *
     * @param carrier      the text headers carrier
     * @param headerSetter a setter implementing the actual addition of headers to the headers carrier
     * @param headerGetter a getter for headers of the carries. Used to detect already present headers to prevent overriding.
     *                     If not provided, no such check will be performed.
     * @param <C>          the header carrier type, for example - an HTTP request
     */
    <C> void propagateContext(C carrier, TextHeaderSetter<C> headerSetter, @Nullable TextHeaderGetter<C> headerGetter);

    /**
     * Same as {@link #propagateContext(Object, TextHeaderSetter, TextHeaderGetter)}, except that different types can be used
     * for the getter and setter carriers (e.g. builder vs request).
     *
     * @param carrier      the text headers carrier
     * @param headerSetter a setter implementing the actual addition of headers to the headers carrier
     * @param headerGetter a getter for headers of the carries. Used to detect already present headers to prevent overriding.
     *                     If not provided, no such check will be performed.
     * @param <C>          the header carrier type, for example - an HTTP request
     */
    <C1, C2> void propagateContext(C1 carrier, TextHeaderSetter<C1> headerSetter, @Nullable C2 carrier2, @Nullable TextHeaderGetter<C2> headerGetter);

    /**
     * Checks if a call to {@link #propagateContext(Object, TextHeaderSetter, TextHeaderGetter)} would modify the headers of this carrier.
     * In other words, this method can be used as a precheck to see whether a propagation is required.
     * <p>
     * This allows the delay and avoidance of creating costly resources, e.g. builder.
     *
     * @param carrier      the carrier to read headers from
     * @param headerGetter a getter for headers of the carries
     * @param <C>          the carrier type
     * @return true, if a call to propagateContext would modify the headers of the carrier
     */
    <C> boolean isPropagationRequired(C carrier, TextHeaderGetter<C> headerGetter);

}
