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
package co.elastic.apm.agent.webflux.client;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.codec.Hints;
import org.springframework.util.ObjectUtils;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public abstract class WebfluxClientInstrumentation extends TracerAwareInstrumentation {

    public static final Logger logger = LoggerFactory.getLogger(WebfluxClientInstrumentation.class);

    public static class DefaultWebClientConstructor extends WebfluxClientInstrumentation {
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return isConstructor();
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("org.springframework.web.reactive.function.client.DefaultWebClient");
        }

        @Nullable
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void onAfter(
            @Advice.Argument(0) ExchangeFunction exchangeFunction,
            @Advice.This Object thiz,
            @Advice.Origin Class<?> clazz
        ) {
            String exchangeFunctionKey = ObjectUtils.getIdentityHexString(exchangeFunction);
            Transaction t = WebfluxClientHelper.getOrCreateTransaction(tracer, clazz);
            WebfluxClientSubscriber.getWebClientMap().put(exchangeFunctionKey, t);
        }
    }

    public static class ExchangeFunctionExchange extends WebfluxClientInstrumentation {
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("exchange");
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return hasSuperType(named("org.springframework.web.reactive.function.client.ExchangeFunction").and(isInterface()));
        }

        @AssignTo.Return(index = 0)
        @Nullable
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static Object[] onAfter(
            @Advice.Argument(0) ClientRequest clientRequest,
            @Advice.Return Mono<ClientResponse> clientResponseMono,
            @Advice.This Object thiz
        ) {
            String exchangeKey = ObjectUtils.getIdentityHexString(thiz);
            String monoKey = ObjectUtils.getIdentityHexString(clientResponseMono);
            Transaction t = (Transaction) WebfluxClientSubscriber.getWebClientMap().get(exchangeKey);
            if (t != null) {
                AbstractSpan httpSpan = WebfluxClientHelper.createHttpSpan(t, clientRequest.method(), clientRequest.url());
                WebfluxClientSubscriber.getLogPrefixMap().put(clientRequest.logPrefix(), httpSpan);
                return new Object[]{WebfluxClientHelper.wrapSubscriber((Publisher) clientResponseMono, clientRequest.logPrefix(), tracer,
                    "ExchangeFunctionsExchange-" + monoKey)};
            } else {
                return new Object[]{clientResponseMono};
            }
        }
    }

    public static class DefaultClientResponseBody extends WebfluxClientInstrumentation {
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("body");
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("org.springframework.web.reactive.function.client.DefaultClientResponse");
        }

        @AssignTo.Return(index = 0)
        @Nullable
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static Object[] onAfter(
            @Advice.Return Object result,
            @Advice.FieldValue("logPrefix") String logPrefix
        ) {
            //FIXME: probably dont need to key to the flux
            String fluxKey = ObjectUtils.getIdentityHexString(result);
            if (result instanceof Publisher) {
                return new Object[]{WebfluxClientHelper.wrapSubscriber((Publisher) result, logPrefix, tracer,
                    "DefaultClientResponseBodyLift-" + fluxKey)};
            } else {
                return new Object[]{result};
            }
        }
    }

    public static class BodyInsertersWriteStatic extends WebfluxClientInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("org.springframework.web.reactive.function.BodyInserters");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return isStatic().and(named("write").and(takesArgument(0, Publisher.class)));
        }

        @Nonnull
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onBefore(
            @Advice.Argument(0) Publisher publisher,
            @Advice.Argument(4) BodyInserter.Context context
        ) {
            if (context.hints() != null && context.hints().containsKey(Hints.LOG_PREFIX_HINT)) {
                String logPrefix = (String) context.hints().get(Hints.LOG_PREFIX_HINT);

                publisher.subscribe(new WebfluxClientSubscriber(null, logPrefix, tracer, "BodySend-BodyInserters-"));
            }
        }
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "spring-webflux", "experimental");
    }

}
