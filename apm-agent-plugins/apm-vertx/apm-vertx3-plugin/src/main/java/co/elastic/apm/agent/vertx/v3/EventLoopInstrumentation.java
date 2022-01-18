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
package co.elastic.apm.agent.vertx.v3;

import co.elastic.apm.agent.vertx.GenericHandlerWrapper;
import co.elastic.apm.agent.vertx.SetTimerWrapper;
import io.vertx.core.Handler;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.isPackagePrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public abstract class EventLoopInstrumentation extends Vertx3Instrumentation {

    /**
     * Instruments {@link io.vertx.core.impl.VertxImpl#setTimer} to propagate context through {@link GenericHandlerWrapper} into async Vert.X tasks.
     */
    public static class SetTimerInstrumentation extends EventLoopInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("io.vertx.core.impl.VertxImpl");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("setTimer")
                .and(takesArgument(1, named("io.vertx.core.Handler")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx.v3.EventLoopInstrumentation$SetTimerInstrumentation$SetTimerAdvice";
        }

        public static class SetTimerAdvice {

            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            @Advice.AssignReturned.ToArguments(@ToArgument(value = 1))
            public static Handler<Long> setTimerEnter(@Advice.Argument(value = 1) Handler<Long> handler) {
                return SetTimerWrapper.wrapTimerIfActiveSpan(handler);
            }
        }

    }

    /**
     * Instruments {@link io.vertx.core.impl.VertxImpl#runOnContext}  to propagate context through {@link GenericHandlerWrapper}
     * into onContext execution of Vert.X tasks.
     */
    public static class OnContextInstrumentation extends EventLoopInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("io.vertx.core.impl.ContextImpl");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("runOnContext")
                .and(takesArgument(0, named("io.vertx.core.Handler")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx.v3.EventLoopInstrumentation$ExecuteOnContextAdvice";
        }

    }

    /**
     * Instruments {@link io.vertx.core.impl.VertxImpl#executeBlocking} to propagate context through {@link GenericHandlerWrapper}
     * into blocking Vert.X tasks.
     */
    public static class ExecuteBlockingInstrumentation extends EventLoopInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("io.vertx.core.impl.ContextImpl");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("executeBlocking")
                .and(takesArgument(0, named("io.vertx.core.Handler")))
                .and(takesArgument(1, named("io.vertx.core.Handler")))
                .and(isPackagePrivate());
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx.v3.EventLoopInstrumentation$ExecuteOnContextAdvice";
        }
    }

    public static class ExecuteOnContextAdvice {

        @Advice.AssignReturned.ToArguments(@ToArgument(value = 0))
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Handler<?> executeBlockingEnter(@Advice.Argument(value = 0) Handler<?> handler) {
            return GenericHandlerWrapper.wrapIfActiveSpan(handler);
        }
    }
}
