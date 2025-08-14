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
package co.elastic.apm.agent.httpclient.v5;

import co.elastic.apm.agent.httpclient.common.AbstractApacheHttpClientAsyncAdvice;
import co.elastic.apm.agent.httpclient.v5.helper.ApacheHttpClient5AsyncHelper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.protocol.HttpContext;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ApacheHttpAsyncClient5Instrumentation extends BaseApacheHttpClient5Instrumentation {

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.httpclient.v5.ApacheHttpAsyncClient5Instrumentation$ApacheHttpClient5AsyncAdvice";
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader()).and(classLoaderCanLoadClass("org.apache.hc.client5.http.async.HttpAsyncClient"));
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("HttpAsyncClient");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        // the execute public method can be called with a null callback, in which case the wrapped callback will never
        // be called by the client code, and will block the caller indefinitely. The doExecute method wraps it another
        // time and returns it, and the caller is expected to call this returned value.
        return named("doExecute").and(takesArguments(6))
            .and(takesArgument(0, named("org.apache.hc.core5.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.hc.core5.http.nio.AsyncRequestProducer")))
            .and(takesArgument(2, named("org.apache.hc.core5.http.nio.AsyncResponseConsumer")))
            .and(takesArgument(3, named("org.apache.hc.core5.http.nio.HandlerFactory")))
            .and(takesArgument(4, named("org.apache.hc.core5.http.protocol.HttpContext")))
            .and(takesArgument(5, named("org.apache.hc.core5.concurrent.FutureCallback")));
    }

    public static class ApacheHttpClient5AsyncAdvice extends AbstractApacheHttpClientAsyncAdvice {

        private static ApacheHttpClient5AsyncHelper asyncHelper = new ApacheHttpClient5AsyncHelper();

        @Nullable
        @Advice.AssignReturned.ToArguments({
            @Advice.AssignReturned.ToArguments.ToArgument(index = 0, value = 1, typing = DYNAMIC),
            @Advice.AssignReturned.ToArguments.ToArgument(index = 1, value = 5, typing = DYNAMIC)
        })
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object[] onBeforeExecute(@Advice.Argument(value = 1) AsyncRequestProducer asyncRequestProducer,
                                               @Advice.Argument(value = 4) HttpContext context,
                                               @Advice.Argument(value = 5) FutureCallback<?> futureCallback) {
            return startSpan(tracer, asyncHelper, asyncRequestProducer, context, futureCallback);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Enter @Nullable Object[] enter, @Advice.Thrown @Nullable Throwable t) {
            endSpan(asyncHelper, enter, t);
        }
    }
}
