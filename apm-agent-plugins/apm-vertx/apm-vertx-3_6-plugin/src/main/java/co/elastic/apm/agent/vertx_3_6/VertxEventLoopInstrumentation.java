/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.vertx_3_6;

import co.elastic.apm.agent.vertx.GenericHandlerWrapper;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.isPackagePrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public abstract class VertxEventLoopInstrumentation extends VertxWebInstrumentation {

    public static final String VERTX_EVENTS_INSTRUMENTATION_GROUP = "vertx-events";

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("vertx", VERTX_EVENTS_INSTRUMENTATION_GROUP);
    }

    /**
     * Instruments {@link io.vertx.core.impl.VertxImpl#setTimer} to propagate context through {@link GenericHandlerWrapper} into async Vert.X tasks.
     */
    public static class SetTimerInstrumentation extends VertxEventLoopInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("io.vertx.core.impl.VertxImpl");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("setTimer").and(takesArgument(1, named("io.vertx.core.Handler")));
        }

        @Override
        public Class<?> getAdviceClass() {
            return SetTimerAdvice.class;
        }
    }

    /**
     * Instruments {@link io.vertx.core.impl.VertxImpl#runOnContext}  to propagate context through {@link GenericHandlerWrapper}
     * into onContext execution of Vert.X tasks.
     */
    public static class OnContextInstrumentation extends VertxEventLoopInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("io.vertx.core.impl.ContextImpl");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("runOnContext").and(takesArgument(0, named("io.vertx.core.Handler")));
        }

        @Override
        public Class<?> getAdviceClass() {
            return ExecuteOnContextAdvice.class;
        }
    }

    /**
     * Instruments {@link io.vertx.core.impl.VertxImpl#executeBlocking} to propagate context through {@link GenericHandlerWrapper}
     * into blocking Vert.X tasks.
     */
    public static class ExecuteBlockingInstrumentation extends VertxEventLoopInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("io.vertx.core.impl.ContextImpl");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("executeBlocking").and(takesArgument(0, named("io.vertx.core.Handler")))
                .and(takesArgument(1, named("io.vertx.core.Handler"))).and(isPackagePrivate());
        }

        @Override
        public Class<?> getAdviceClass() {
            return ExecuteOnContextAdvice.class;
        }
    }
}
