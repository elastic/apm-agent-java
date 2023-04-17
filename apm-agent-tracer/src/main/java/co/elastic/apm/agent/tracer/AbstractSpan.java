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

import co.elastic.apm.agent.tracer.dispatch.BinaryHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.BinaryHeaderSetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;

import javax.annotation.Nullable;

public interface AbstractSpan<T extends AbstractSpan<T>> extends ElasticContext<T> {

    int PRIORITY_DEFAULT = 0;
    int PRIORITY_LOW_LEVEL_FRAMEWORK = 10;
    int PRIORITY_METHOD_SIGNATURE = 10 * PRIORITY_LOW_LEVEL_FRAMEWORK;
    int PRIORITY_HIGH_LEVEL_FRAMEWORK = 10 * PRIORITY_LOW_LEVEL_FRAMEWORK;
    int PRIORITY_USER_SUPPLIED = 100 * PRIORITY_LOW_LEVEL_FRAMEWORK;

    AbstractContext getContext();

    TraceContext getTraceContext();

    /**
     * Sets Trace context binary headers, using this context as parent, on the provided carrier using the provided setter
     *
     * @param carrier      the binary headers carrier
     * @param headerSetter a setter implementing the actual addition of headers to the headers carrier
     * @param <C>          the header carrier type, for example - a Kafka record
     * @return true if Trace Context headers were set; false otherwise
     */
    <C> boolean propagateTraceContext(C carrier, BinaryHeaderSetter<C> headerSetter);

    /**
     * Sets Trace context text headers, using this context as parent, on the provided carrier using the provided setter
     *
     * @param carrier      the text headers carrier
     * @param headerSetter a setter implementing the actual addition of headers to the headers carrier
     * @param <C>          the header carrier type, for example - an HTTP request
     */
    <C> void propagateTraceContext(C carrier, TextHeaderSetter<C> headerSetter);

    Span<?> createSpan();

    /**
     * Creates a child Span representing a remote call event, unless this TraceContextHolder already represents an exit event.
     * If current TraceContextHolder is representing an Exit- returns null
     *
     * @return an Exit span if this TraceContextHolder is not an exit span, null otherwise
     */
    @Nullable
    Span<?> createExitSpan();

    void end();

    T captureException(@Nullable Throwable t);

    @Nullable
    String getType();

    T withType(@Nullable String type);

    /**
     * Determines whether to discard the span.
     * Only spans that return {@code false} are reported.
     * <p>
     * A span is discarded if it is discardable and {@linkplain #requestDiscarding() requested to be discarded}.
     * </p>
     *
     * @return {@code true}, if the span should be discarded, {@code false} otherwise.
     */
    boolean isDiscarded();

    /**
     * Requests this span to be discarded, even if it's sampled.
     * <p>
     * Whether the span can actually be discarded is determined by {@link #isDiscarded()}
     * </p>
     */
    T requestDiscarding();

    /**
     * Sets this context as non-discardable,
     * meaning that {@link AbstractSpan#isDiscarded()} will return {@code false},
     * even if {@link AbstractSpan#requestDiscarding()} has been called.
     */
    void setNonDiscardable();

    boolean isFinished();

    boolean isSampled();

    <C> boolean addLink(BinaryHeaderGetter<C> headerGetter, @Nullable C carrier);

    <C> boolean addLink(TextHeaderGetter<C> headerGetter, @Nullable C carrier);

    /**
     * Appends a string to the name.
     * <p>
     * This method helps to avoid the memory allocations of string concatenations
     * as the underlying {@link StringBuilder} instance will be reused.
     * </p>
     *
     * @param cs the char sequence to append to the name
     * @return {@code this}, for chaining
     */
    T appendToName(CharSequence cs);

    T appendToName(CharSequence cs, int priority);

    T appendToName(CharSequence cs, int priority, int startIndex, int endIndex);

    T withName(@Nullable String name);

    T withName(@Nullable String name, int priority);

    T withName(@Nullable String name, int priority, boolean overrideIfSamePriority);

    /**
     * Resets and returns the name {@link StringBuilder} if the provided priority is {@code >=} the current priority.
     * Otherwise, returns {@code null}
     *
     * @param namePriority the priority for the name. See also the {@code AbstractSpan#PRIO_*} constants.
     * @return the name {@link StringBuilder} if the provided priority is {@code >=} the current priority, {@code null} otherwise.
     */
    @Nullable
    StringBuilder getAndOverrideName(int namePriority);

    /**
     * Resets and returns the name {@link StringBuilder} if one of the following applies:
     * <ul>
     *      <li>the provided priority is {@code >} the current priority</li>
     *      <li>the provided priority is {@code ==} the current priority AND {@code overrideIfSamePriority} is {@code true}</li>
     * </ul>
     * Otherwise, returns {@code null}
     *
     * @param namePriority           the priority for the name. See also the {@code AbstractSpan#PRIO_*} constants.
     * @param overrideIfSamePriority specifies whether the existing name should be overridden if {@code namePriority} equals the priority used to set the current name
     * @return the name {@link StringBuilder} if the provided priority is sufficient for overriding, {@code null} otherwise.
     */
    @Nullable
    StringBuilder getAndOverrideName(int namePriority, boolean overrideIfSamePriority);

    /**
     * @return user outcome if set, otherwise outcome value
     */
    Outcome getOutcome();

    /**
     * Sets outcome
     *
     * @param outcome outcome
     * @return this
     */
    T withOutcome(Outcome outcome);

    T withSync(boolean sync);

    void incrementReferences();

    void decrementReferences();
}
