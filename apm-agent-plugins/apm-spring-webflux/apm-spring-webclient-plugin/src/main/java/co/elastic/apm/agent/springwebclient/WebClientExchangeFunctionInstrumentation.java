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
package co.elastic.apm.agent.springwebclient;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.reactivestreams.Publisher;
import org.springframework.web.reactive.function.client.ClientRequest;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class WebClientExchangeFunctionInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("ExchangeFunction");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.springframework.web.reactive.function.client.ExchangeFunction"))
            .and(not(isInterface()));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("exchange")
            .and(takesArgument(0, named("org.springframework.web.reactive.function.client.ClientRequest")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "spring-webclient");
    }

    public static class AdviceClass {

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @Advice.AssignReturned.ToArguments(@ToArgument(index = 0, value = 0, typing = Assigner.Typing.DYNAMIC))
        public static Object[] onBefore(@Advice.Argument(0) ClientRequest clientRequest) {
            final AbstractSpan<?> parent = tracer.getActive();
            if (parent == null) {
                return null;
            }
            ClientRequest.Builder builder = ClientRequest.from(clientRequest);
            URI uri = clientRequest.url();
            Span<?> span = HttpClientHelper.startHttpClientSpan(parent, clientRequest.method().name(), uri, uri.getHost());
            if (span != null) {
                span.activate();
                span.propagateTraceContext(builder, WebClientRequestHeaderSetter.INSTANCE);
            } else {
                parent.propagateTraceContext(builder, WebClientRequestHeaderSetter.INSTANCE);
            }
            clientRequest = builder.build();
            return new Object[]{clientRequest, span};
        }


        @Advice.AssignReturned.ToReturned(typing = Assigner.Typing.DYNAMIC)
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static Object afterExecute(@Advice.Return @Nullable Publisher<?> returnValue,
                                          @Advice.Enter @Nullable Object[] spanRequestObj,
                                          @Advice.Thrown @Nullable Throwable t) {
            if (spanRequestObj == null || spanRequestObj.length < 2) {
                return returnValue;
            }
            Object spanObj = spanRequestObj[1];
            if (!(spanObj instanceof Span<?>)) {
                return returnValue;
            }
            Span<?> span = (Span<?>) spanObj;
            span = span.captureException(t).deactivate();
            if (t != null || returnValue == null) {
                return returnValue;
            }
            return WebClientHelper.wrapSubscriber(returnValue, span, tracer);
        }
    }
}
