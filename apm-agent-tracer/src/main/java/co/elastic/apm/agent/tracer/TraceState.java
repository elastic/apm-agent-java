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

import co.elastic.apm.agent.tracer.dispatch.HeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.HeaderSetter;
import co.elastic.apm.agent.tracer.reference.ReferenceCounted;

import javax.annotation.Nullable;

public interface TraceState<T extends TraceState<T>> extends ActivateableInScope<T>, ReferenceCounted {

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
     * @return the baggage associated with this context
     */
    Baggage getBaggage();

    /**
     * Creates a child span of this context, if possible.
     * Guaranteed to be non-null if {@link #getSpan()} returns non null.
     *
     * @return the newly created span with this context as parent.
     */
    @Nullable
    Span<?> createSpan();

    /**
     * Creates a child Span representing a remote call event, unless this TraceContextHolder already represents an exit event.
     * If current TraceContextHolder is representing an Exit- returns null
     *
     * @return an Exit span if this TraceContextHolder is not an exit span, null otherwise
     */
    @Nullable
    Span<?> createExitSpan();

    BaggageContextBuilder withUpdatedBaggage();

    /**
     * If a context is empty, it does not need to be propagated (neither within the process, nor via external calls).
     *
     * @return true, if this context contains nothing (neither a span, nor baggage nor anything else for wrapped contexts).
     */
    boolean isEmpty();

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
    <C> void propagateContext(C carrier, HeaderSetter<?, C> headerSetter, @Nullable HeaderGetter<?, C> headerGetter);

    /**
     * Same as {@link #propagateContext(Object, HeaderSetter, HeaderGetter)}, except that different types can be used
     * for the getter and setter carriers (e.g. builder vs request).
     *
     * @param carrier      the text headers carrier for setting header
     * @param headerSetter a setter implementing the actual addition of headers to the headers carrier
     * @param carrier2     the text headers carrier for setting header
     * @param headerGetter a getter for headers of the carries. Used to detect already present headers to prevent overriding.
     *                     If not provided, no such check will be performed.
     * @param <C1>         the header carrier type for writing headers
     * @param <C2>         the header carrier type for reading headers
     */
    <C1, C2> void propagateContext(C1 carrier, HeaderSetter<?, C1> headerSetter, @Nullable C2 carrier2, @Nullable HeaderGetter<?, C2> headerGetter);

    /**
     * Checks if a call to {@link #propagateContext(Object, HeaderSetter, HeaderGetter)} would modify the headers of this carrier.
     * In other words, this method can be used as a precheck to see whether a propagation is required.
     * <p>
     * This allows the delay and avoidance of creating costly resources, e.g. builder.
     *
     * @param carrier      the carrier to read headers from
     * @param headerGetter a getter for headers of the carries
     * @param <C>          the carrier type
     * @return true, if a call to propagateContext would modify the headers of the carrier
     */
    <C> boolean isPropagationRequired(C carrier, HeaderGetter<?, C> headerGetter);

    /**
     * @return {@literal true} when span limit is reached and the caller can optimize and not create a span. The caller
     * is expected to call this method before every span creation operation for proper dropped spans accounting. If not
     * called before attempting span creation, a span will be created and dropped before reporting.
     * <br>
     * Expected caller behavior depends on the returned value:
     * <ul>
     *     <li>{@literal true} returned means the caller is expected to NOT call {@link #createSpan()} or {@link #createExitSpan()}</li>
     *     <li>{@literal false} returned means the caller MAY call {@link #createSpan()} or {@link #createExitSpan()}</li>
     * </ul>
     */
    boolean shouldSkipChildSpanCreation();

}
