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
package co.elastic.apm.agent.scalaconcurrent;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.collections.WeakConcurrentProviderImpl;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
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

    @SuppressWarnings("WeakerAccess")
    public static final WeakMap<Object, AbstractSpan<?>> promisesToContext = WeakConcurrentProviderImpl.createWeakSpanMap();

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

        public static class AdviceClass {
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void onExit(@Advice.This Object thiz) {
                final AbstractSpan<?> context = tracer.getActive();
                if (context != null) {
                    promisesToContext.put(thiz, context);
                }
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

        public static class AdviceClass {
            @Nullable
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Object onEnter(@Advice.This Object thiz) {
                // We cannot remove yet, as this may decrement the ref count of the span to 0 if it has already ended,
                // thus causing it to be recycled just before we activate it on the current thread. So we first get().
                AbstractSpan<?> context = promisesToContext.get(thiz);
                if (context != null) {
                    context.activate();
                    // Now it's safe to remove, as ref count is at least 2
                    promisesToContext.remove(thiz);
                }
                return context;
            }

            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void onExit(@Advice.Enter @Nullable Object abstractSpanObj) {
                if (abstractSpanObj instanceof AbstractSpan<?>) {
                    AbstractSpan<?> context = (AbstractSpan<?>) abstractSpanObj;
                    context.deactivate();
                }
            }
        }
    }
}
