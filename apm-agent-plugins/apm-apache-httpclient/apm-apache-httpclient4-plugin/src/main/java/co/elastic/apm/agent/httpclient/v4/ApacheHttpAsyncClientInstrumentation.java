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
package co.elastic.apm.agent.httpclient.v4;

import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.httpclient.v4.helper.ApacheHttpAsyncClientHelper;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.protocol.HttpContext;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ApacheHttpAsyncClientInstrumentation extends BaseApacheHttpClientInstrumentation {

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.httpclient.v4.ApacheHttpAsyncClientInstrumentation$ApacheHttpAsyncClientAdvice";
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(classLoaderCanLoadClass("org.apache.http.nio.client.HttpAsyncClient"));
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("HttpAsyncClient");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.apache.http.nio.client.HttpAsyncClient"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute")
            .and(takesArguments(4))
            .and(takesArgument(0, named("org.apache.http.nio.protocol.HttpAsyncRequestProducer")))
            .and(takesArgument(1, named("org.apache.http.nio.protocol.HttpAsyncResponseConsumer")))
            .and(takesArgument(2, named("org.apache.http.protocol.HttpContext")))
            .and(takesArgument(3, named("org.apache.http.concurrent.FutureCallback")));
    }

    public static class ApacheHttpAsyncClientAdvice {
        private static ApacheHttpAsyncClientHelper asyncHelper = new ApacheHttpAsyncClientHelper();

        @Advice.AssignReturned.ToArguments({
            @ToArgument(index = 0, value = 0, typing = DYNAMIC),
            @ToArgument(index = 1, value = 3, typing = DYNAMIC)
        })
        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object[] onBeforeExecute(@Advice.Argument(value = 0) HttpAsyncRequestProducer requestProducer,
                                               @Advice.Argument(2) HttpContext context,
                                               @Advice.Argument(value = 3) FutureCallback<?> futureCallback) {
            AbstractSpan<?> parent = tracer.getActive();
            if (parent == null) {
                return null;
            }
            Span<?> span = parent.createExitSpan();
            HttpAsyncRequestProducer wrappedProducer = requestProducer;
            FutureCallback<?> wrappedFutureCallback = futureCallback;
            boolean responseFutureWrapped = false;
            if (span != null) {
                span.withType(HttpClientHelper.EXTERNAL_TYPE)
                    .withSubtype(HttpClientHelper.HTTP_SUBTYPE)
                    .activate();

                wrappedProducer = asyncHelper.wrapRequestProducer(requestProducer, span, null);
                wrappedFutureCallback = asyncHelper.wrapFutureCallback(futureCallback, context, span);
                responseFutureWrapped = true;
            } else {
                wrappedProducer = asyncHelper.wrapRequestProducer(requestProducer, null, parent);
            }
            return new Object[]{wrappedProducer, wrappedFutureCallback, responseFutureWrapped, span};
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Enter @Nullable Object[] enter,
                                          @Advice.Thrown @Nullable Throwable t) {
            Span<?> span = enter != null ? (Span<?>) enter[3] : null;
            if (span != null) {
                // Deactivate in this thread. Span will be ended and reported by the listener
                span.deactivate();

                if (!((Boolean) enter[2])) {
                    // Listener is not wrapped- we need to end the span so to avoid leak and report error if occurred during method invocation
                    span.captureException(t);
                    span.end();
                }
            }
        }
    }
}
