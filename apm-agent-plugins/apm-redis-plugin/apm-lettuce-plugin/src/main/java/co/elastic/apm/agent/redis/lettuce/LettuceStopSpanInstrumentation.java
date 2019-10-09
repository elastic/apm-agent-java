/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.redis.lettuce;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import io.lettuce.core.protocol.RedisCommand;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Stops the span created by {@link LettuceStartSpanInstrumentation} when one of the following methods are called:
 * <ul>
 *     <li>{@link RedisCommand#complete()}</li>
 *     <li>{@link RedisCommand#completeExceptionally(Throwable)}</li>
 *     <li>{@link RedisCommand#cancel()}</li>
 * </ul>
 * Rather than wrapping the {@link RedisCommand}, the context propagation relies on the Netty instrumentation.
 */
public abstract class LettuceStopSpanInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.lettuce.core.protocol.Command");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("redis", "lettuce");
    }

    public static class OnComplete extends LettuceStopSpanInstrumentation {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void beforeComplete() {
            LettuceUtil.beforeComplete(null);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("complete").and(takesArguments(0));
        }
    }

    public static class OnCompleteExceptionally extends LettuceStopSpanInstrumentation {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void beforeComplete(@Advice.Argument(0) Throwable throwable) {
            LettuceUtil.beforeComplete(throwable);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("completeExceptionally").and(takesArguments(Throwable.class));
        }
    }

    public static class OnCancel extends LettuceStopSpanInstrumentation {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void beforeComplete() {
            LettuceUtil.beforeComplete(null);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("cancel").and(takesArguments(0));
        }
    }

    public static class LettuceUtil {

        @VisibleForAdvice
        public static void beforeComplete(@Nullable Throwable t) {
            TraceContextHolder<?> active = getActive();
            if (active instanceof Span) {
                Span activeSpan = (Span) active;
                if ("redis".equals(activeSpan.getSubtype())) {
                    activeSpan
                        .captureException(t)
                        .end();
                }
            }
        }
    }
}
