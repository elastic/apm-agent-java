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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.concurrent.JavaConcurrent;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class AsyncInstrumentation extends AbstractServletInstrumentation {

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList(Constants.SERVLET_API, Constants.SERVLET_API_ASYNC);
    }

    public abstract static class StartAsyncInstrumentation extends AsyncInstrumentation {

        @Override
        public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
            return nameContains("Request");
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return not(isInterface())
                .and(hasSuperType(getImplConstants().requestClass()));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return isPublic()
                .and(named("startAsync"))
                .and(returns(hasSuperType(getImplConstants().asyncContextClass())))
                .and(takesArguments(0)
                    .or(
                        takesArgument(0, getImplConstants().requestClass())
                            .and(takesArgument(1, getImplConstants().responseClass()))
                    )
                );
        }

    }

    public abstract static class AsyncContextInstrumentation extends AsyncInstrumentation {

        @Override
        public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
            return nameContains("AsyncContext");
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return not(isInterface())
                .and(hasSuperType(getImplConstants().asyncContextClass()));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return isPublic()
                .and(named("start"))
                .and(takesArguments(Runnable.class));
        }

        public static class AsyncContextStartAdvice {

            @Nullable
            @Advice.AssignReturned.ToArguments(@ToArgument(0))
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Runnable onEnterAsyncContextStart(@Advice.Argument(0) @Nullable Runnable runnable) {
                return JavaConcurrent.withContext(runnable, tracer);
            }

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Exception.class, inline = false)
            public static void onExitAsyncContextStart(@Nullable @Advice.Thrown Throwable thrown,
                                                       @Advice.Argument(value = 0) @Nullable Runnable runnable) {
                JavaConcurrent.doFinally(thrown, runnable);
            }
        }
    }
}
