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

import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.TraceState;
import co.elastic.apm.agent.tracer.Tracer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ClientHttpRequest;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments all {@link org.springframework.http.client.reactive.ClientHttpConnector} types, to preserve the span context
 * within the callback passed to {@link org.springframework.http.client.reactive.ClientHttpConnector#connect(HttpMethod, URI, Function)}.
 * If the span is ready for it, this will cause the request body to be captured.
 */
public class ClientHttpConnectorInstrumentation extends ElasticApmInstrumentation {

    private static final Tracer tracer = GlobalTracer.get();

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameStartsWith("org.springframework.http.")
            .and(nameContains("Connector"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.springframework.http.client.reactive.ClientHttpConnector"))
            .and(not(isInterface()));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("connect")
            .and(takesArgument(2, named("java.util.function.Function")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "spring-webclient");
    }

    public static class AdviceClass {

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @Advice.AssignReturned.ToArguments(@Advice.AssignReturned.ToArguments.ToArgument(2))
        public static Function<? super ClientHttpRequest, Mono<Void>> onBefore(@Advice.Argument(2) Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {
            TraceState<?> context = tracer.currentContext();
            if (context.isEmpty()) {
                return requestCallback;
            }
            return new Function<ClientHttpRequest, Mono<Void>>() {
                @Override
                public Mono<Void> apply(ClientHttpRequest clientHttpRequest) {
                    // Note that even though ClientHttpRequest exposes headers via the interface, those are empty
                    // therefore we check the span capturing pre-conditions in the WebClientExchangeFunctionInstrumentation instead
                    BodyCaptureRegistry.maybeCaptureBodyFor(context.getSpan(), clientHttpRequest);
                    return requestCallback.apply(clientHttpRequest);
                }
            };
        }

    }
}
