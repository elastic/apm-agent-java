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
package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public interface AbstractSpan<T extends AbstractSpan<T>> extends ElasticContext<T> {

    int PRIO_USER_SUPPLIED = 1000;
    int PRIO_HIGH_LEVEL_FRAMEWORK = 100;
    int PRIO_METHOD_SIGNATURE = 100;
    int PRIO_LOW_LEVEL_FRAMEWORK = 10;
    int PRIO_DEFAULT = 0;
    int MAX_ALLOWED_SPAN_LINKS = 1000;

    AbstractContext getContext();

    boolean isDiscarded();

    boolean isReferenced();

    boolean isFinished();

    boolean isSampled();

    @Nullable
    String getType();

    @Nullable
    StringBuilder getAndOverrideName(int namePriority);

    @Nullable
    StringBuilder getAndOverrideName(int namePriority, boolean overrideIfSamePriority);

    void updateName(Class<?> clazz, String methodName);

    void setNonDiscardable();

    T requestDiscarding();

    T appendToName(CharSequence cs);

    T appendToName(CharSequence cs, int priority);

    T appendToName(CharSequence cs, int priority, int startIndex, int endIndex);

    T withName(@Nullable String name);

    T withName(@Nullable String name, int priority);

    T withName(@Nullable String name, int priority, boolean overrideIfSamePriority);

    T withType(@Nullable String type);

    T withSync(boolean sync);

    TraceContext getTraceContext();

    <H, C> boolean addSpanLink(
        HeaderChildContextCreator<H, C> childContextCreator,
        HeaderGetter<H, C> headerGetter,
        @Nullable C carrier
    );

    Span<?> createSpan();

    Span<?> createSpan(long epochMicros);

    T asExit();

    T captureException(@Nullable Throwable t);

    void endExceptionally(@Nullable Throwable t);

    void end();

    void end(long epochMicros);

    <C> boolean propagateTraceContext(C carrier, BinaryHeaderSetter<C> headerSetter);

    <C> void propagateTraceContext(C carrier, TextHeaderSetter<C> headerSetter);

    Outcome getOutcome();

    T withOutcome(Outcome outcome);

    Span<?> createExitSpan();

    void incrementReferences();

    void decrementReferences();

    int getReferenceCount();
}
