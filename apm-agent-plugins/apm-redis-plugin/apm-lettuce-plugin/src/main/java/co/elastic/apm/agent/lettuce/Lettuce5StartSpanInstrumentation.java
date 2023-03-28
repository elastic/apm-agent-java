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
package co.elastic.apm.agent.lettuce;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.collections.WeakConcurrentProviderImpl;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.redis.RedisSpanUtils;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import io.lettuce.core.protocol.RedisCommand;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Starts a span in {@link io.lettuce.core.RedisChannelHandler#dispatch(RedisCommand)}
 *
 * The context will be propagated via the Netty instrumentation
 */
public class Lettuce5StartSpanInstrumentation extends TracerAwareInstrumentation {

    static final WeakMap<RedisCommand<?, ?, ?>, Span> commandToSpan = WeakConcurrentProviderImpl.createWeakSpanMap();

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.lettuce.core.RedisChannelHandler");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("dispatch")
            .and(returns(nameEndsWith("RedisCommand")))
            .and(takesArguments(1))
            .and(takesArgument(0, nameEndsWith("RedisCommand")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("redis", "lettuce");
    }

    public static class AdviceClass {
            @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object beforeDispatch(@Nullable @Advice.Argument(0) RedisCommand<?, ?, ?> command) {
            if (command != null) {
                Span<?> span = RedisSpanUtils.createRedisSpan(command.getType().name());
                if (span != null) {
                    commandToSpan.put(command, span);
                    return span;
                }
            }
            return null;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void afterDispatch(@Nullable @Advice.Enter Object spanObj) {
            Span<?> span = (Span<?>) spanObj;
            if (span != null) {
                span.deactivate();
            }
        }
    }
}
