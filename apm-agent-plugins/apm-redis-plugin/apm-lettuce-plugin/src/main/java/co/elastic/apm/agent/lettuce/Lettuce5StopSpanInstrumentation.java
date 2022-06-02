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
import co.elastic.apm.agent.impl.transaction.Span;
import io.lettuce.core.protocol.RedisCommand;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.lettuce.Lettuce5StartSpanInstrumentation.commandToSpan;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Stops the span created by {@link Lettuce5StartSpanInstrumentation} when one of the following methods are called:
 * <ul>
 *     <li>{@link RedisCommand#complete()}</li>
 *     <li>{@link RedisCommand#completeExceptionally(Throwable)}</li>
 *     <li>{@link RedisCommand#cancel()}</li>
 * </ul>
 */
public abstract class Lettuce5StopSpanInstrumentation extends TracerAwareInstrumentation {

    private static final Logger logger = LoggerFactory.getLogger(Lettuce5StopSpanInstrumentation.class);

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameStartsWith("io.lettuce").and(nameContains("Command"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("io.lettuce.core.protocol.RedisCommand"));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("redis", "lettuce");
    }

    public static class OnComplete extends Lettuce5StopSpanInstrumentation {

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void beforeComplete(@Advice.This RedisCommand<?, ?, ?> command) {
                if (!command.isDone() && !command.isCancelled()) {
                    Span span = Lettuce5StartSpanInstrumentation.commandToSpan.remove(command);
                    if (span != null) {
                        logger.debug("Command#complete");
                        span.end();
                    }
                }
            }
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("complete").and(takesArguments(0));
        }
    }

    public static class OnCompleteExceptionally extends Lettuce5StopSpanInstrumentation {

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void beforeComplete(@Advice.This RedisCommand<?, ?, ?> command, @Advice.Argument(0) Throwable throwable) {
                if (!command.isDone() && !command.isCancelled()) {
                    Span span = Lettuce5StartSpanInstrumentation.commandToSpan.remove(command);
                    if (span != null) {
                        logger.debug("Command#completeExceptionally");
                        span.captureException(throwable).end();
                    }
                }
            }
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("completeExceptionally").and(takesArguments(Throwable.class));
        }
    }

    public static class OnCancel extends Lettuce5StopSpanInstrumentation {

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void beforeComplete(@Advice.This RedisCommand<?, ?, ?> command) {
                if (!command.isDone() && !command.isCancelled()) {
                    Span span = commandToSpan.remove(command);
                    if (span != null) {
                        logger.debug("Command#cancel");
                        span.end();
                    }
                }
            }
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("cancel").and(takesArguments(0));
        }
    }

}
