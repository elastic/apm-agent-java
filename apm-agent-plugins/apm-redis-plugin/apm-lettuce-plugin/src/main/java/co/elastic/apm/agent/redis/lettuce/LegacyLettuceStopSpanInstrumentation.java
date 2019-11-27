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

import co.elastic.apm.agent.bci.VisibleForAdvice;
import com.lambdaworks.redis.protocol.Command;
import com.lambdaworks.redis.protocol.RedisCommand;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Stops the span created by {@link Lettuce5StartSpanInstrumentation} when one of the following methods are called:
 * <ul>
 *     <li>{@link RedisCommand#complete()}</li>
 *     <li>{@link RedisCommand#completeExceptionally(Throwable)}</li>
 *     <li>{@link RedisCommand#cancel()}</li>
 * </ul>
 * Rather than wrapping the {@link RedisCommand}, the context propagation relies on the Netty instrumentation.
 */
public abstract class LegacyLettuceStopSpanInstrumentation extends AbstractLegacyLettuceInstrumentation {

    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(LegacyLettuceStopSpanInstrumentation.class);

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("com.lambdaworks.redis.protocol.Command")
            // introduced in Lettuce 3.4.0
            .and(declaresMethod(named("getType")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("redis", "lettuce");
    }

    public static class OnComplete extends LegacyLettuceStopSpanInstrumentation {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void beforeComplete(@Advice.This Command command) {
            if (!command.isDone() && !command.isCancelled()) {
                logger.debug("Command#complete");
                LettuceUtil.beforeComplete(null);
            }
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("complete").and(takesArguments(0));
        }
    }

    public static class OnCompleteExceptionally extends LegacyLettuceStopSpanInstrumentation {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void beforeComplete(@Advice.This Command command, @Advice.Argument(0) Throwable throwable) {
            if (!command.isDone() && !command.isCancelled()) {
                logger.debug("Command#completeExceptionally");
                LettuceUtil.beforeComplete(throwable);
            }
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("completeExceptionally").and(takesArguments(Throwable.class));
        }
    }

    public static class OnCancel extends LegacyLettuceStopSpanInstrumentation {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void beforeComplete(@Advice.This Command command) {
            if (!command.isDone() && !command.isCancelled()) {
                logger.debug("Command#cancel");
                LettuceUtil.beforeComplete(null);
            }
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("cancel").and(takesArguments(0));
        }
    }

}
