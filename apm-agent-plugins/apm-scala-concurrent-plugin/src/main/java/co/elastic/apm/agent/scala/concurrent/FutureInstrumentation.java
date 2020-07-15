/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.scala.concurrent;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

public abstract class FutureInstrumentation extends TracerAwareInstrumentation {

    @VisibleForAdvice
    @SuppressWarnings("WeakerAccess")
    public static final WeakConcurrentMap<Object, AbstractSpan<?>> promisesToContext =
        new WeakConcurrentMap.WithInlinedExpunction<>();

    @Nonnull
    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("scala-future", "experimental");
    }

    public static class ConstructorInstrumentation extends FutureInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("scala.concurrent.impl.Promise$Transformation");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return isConstructor();
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.This Object thiz) {
            final AbstractSpan<?> context = tracer.getActive();
            if (context != null) {
                promisesToContext.put(thiz, context);
                // this span might be ended before the Promise$Transformation#run method starts
                // we have to avoid that this span gets recycled, even in the above mentioned case
                context.incrementReferences();
            }
        }

    }

    public static class RunInstrumentation extends FutureInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("scala.concurrent.impl.Promise$Transformation");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("run").and(returns(void.class));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.This Object thiz, @Nullable @Advice.Local("context") AbstractSpan<?> context) {
            context = promisesToContext.remove(thiz);
            if (context != null) {
                context.activate();
                // decrements the reference we incremented to avoid that the parent context gets recycled before the promise is run
                // because we have activated it, we can be sure it doesn't get recycled until we deactivate in the OnMethodExit advice
                context.decrementReferences();
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Nullable @Advice.Local("context") AbstractSpan<?> context) {
            if (context != null) {
                context.deactivate();
            }
        }

    }

}
