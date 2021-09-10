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
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.util.ObjectUtils;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class WebSocketClientInstrumentation extends TracerAwareInstrumentation {

    public static final Logger logger = LoggerFactory.getLogger(WebSocketClientInstrumentation.class);

    public static final String APM_PARENT_SPAN = "APM_PARENT_SPAN";

    public static class WebSocketClientExecuteInstrumentation extends WebSocketClientInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return hasSuperType(named("org.springframework.web.reactive.socket.client.WebSocketClient").and(isInterface()));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("execute").and(takesArguments(3));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.webflux.client.WebSocketClientInstrumentation$WebSocketClientExecuteInstrumentation$WebSocketClientExecuteAdvice";
        }

        public static class WebSocketClientExecuteAdvice {
            @AssignTo.Argument(index = 0, value = 2)
            @Nonnull
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Object[] onBefore(
                @Advice.Argument(0) URI url,
                @Advice.Argument(2) WebSocketHandler handler,
                @Advice.Origin Class<?> clazz
            ) {
                Transaction t = tracer.currentTransaction();
                if (t != null) {
                    AbstractSpan httpSpan = WebfluxClientHelper.createHttpSpan(t, HttpMethod.GET, url);

                    WebSocketHandlerWrapper webSocketHandlerWrapper = new WebSocketHandlerWrapper(handler, httpSpan, tracer);
                    return new Object[]{webSocketHandlerWrapper, httpSpan};
                } else {
                    return new Object[]{};
                }
            }

            @AssignTo.Return(index = 0)
            @Nonnull
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static Object[] onAfter(
                @Advice.Enter @Nullable Object[] enter,
                @Advice.Return Mono<Void> monoResult,
                @Advice.This Object source
            ) {
                Span httpSpan = enter[1] != null ? (Span) enter[1] : null;
                String clientKey = ObjectUtils.getIdentityHexString(source);

                if (httpSpan != null && (clientKey != null && !clientKey.isEmpty())) {
                    //FIXME: the httpspan is double entried
                    WebfluxClientSubscriber.getLogPrefixMap().put(clientKey, httpSpan);
                    return new Object[]{WebfluxClientHelper.wrapSubscriber((Publisher) monoResult, clientKey, tracer,
                        "WebSocketClientExecute-" + clientKey)};
                } else {
                    return new Object[]{};
                }
            }
        }

    }

    public static class WebSocketSessionSendInstrumentation extends WebSocketClientInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return hasSuperType(named("org.springframework.web.reactive.socket.WebSocketSession").and(isInterface()));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("send");
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.webflux.client.WebSocketClientInstrumentation$WebSocketSessionSendInstrumentation$WebSocketSessionSendAdvice";
        }

        public static class WebSocketSessionSendAdvice {
            @AssignTo.Return(index = 0)
            @Nonnull
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static Object[] onAfter(
                @Advice.Argument(0) Publisher<WebSocketMessage> messages,
                @Advice.Return Mono<Void> sendResultMono,
                @Advice.This WebSocketSession thiz
            ) {
                String logPrefix = (String) thiz.getAttributes().get(APM_PARENT_SPAN);
                if (logPrefix != null && !logPrefix.isBlank()) {
                    messages.subscribe(new WebfluxClientSubscriber(null, logPrefix, tracer, "WebSocketSessionSendAdvice-"));
                    return new Object[]{WebfluxClientHelper.wrapSubscriber((Publisher) sendResultMono, logPrefix, tracer,
                        "WebSocketSessionSendAdviceResult-")};
                } else {
                    return new Object[]{sendResultMono};
                }
            }
        }

    }

    public static class WebSocketSessionReceiveInstrumentation extends WebSocketClientInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return hasSuperType(named("org.springframework.web.reactive.socket.WebSocketSession").and(isInterface()));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("receive");
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.webflux.client.WebSocketClientInstrumentation$WebSocketSessionReceiveInstrumentation$WebSocketSessionReceiveAdvice";
        }

        public static class WebSocketSessionReceiveAdvice {
            @AssignTo.Return(index = 0)
            @Nonnull
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static Object[] onAfter(
                @Advice.Return Flux<WebSocketMessage> receiveReturnFlux,
                @Advice.This WebSocketSession thiz
            ) {
                String logPrefix = (String) thiz.getAttributes().get(APM_PARENT_SPAN);
                if (logPrefix != null && !logPrefix.isBlank()) {
                    return new Object[]{WebfluxClientHelper.wrapSubscriber((Publisher) receiveReturnFlux, logPrefix, tracer,
                        "WebSocketSessionReceiveAdvice-")};
                } else {
                    return new Object[]{receiveReturnFlux};
                }
            }
        }

    }

    public static class WebSocketSessionCloseInstrumentation extends WebSocketClientInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return hasSuperType(named("org.springframework.web.reactive.socket.WebSocketSession").and(isInterface()));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("close");
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.webflux.client.WebSocketClientInstrumentation$WebSocketSessionCloseInstrumentation$WebSocketSessionCloseAdvice";
        }

        public static class WebSocketSessionCloseAdvice {
            @Nonnull
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void onBefore(
                @Advice.Origin Class clazz,
                @Advice.Origin("#m") String methodName,
                @Advice.AllArguments Object[] arguments,
//            @Advice.Return Flux<WebSocketMessage> closeReturnMono,
                @Advice.This WebSocketSession thiz
            ) {
                //FIXME: probably end WebSession span
                String logPrefix = thiz.getId();
                if (logger.isDebugEnabled()) {
                    logger.debug("WebSocketSessionCloseAdvice attr=" + thiz.getAttributes().get(APM_PARENT_SPAN) + " id=" + thiz.getId());
                }
            }
        }


    }


    public static class WebSocketMessageInstrumentation extends WebSocketClientInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("org.springframework.web.reactive.socket.WebSocketMessage");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return isConstructor();
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.webflux.client.WebSocketClientInstrumentation$WebSocketMessageInstrumentation$WebSocketMessageAdvice";
        }

        public static class WebSocketMessageAdvice {
            @Nonnull
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void onAfter(
                @Advice.Origin Class clazz,
                @Advice.AllArguments Object[] arguments
            ) {
                if (logger.isDebugEnabled()) {
                    logger.debug("WebSocketMessageAdvice attr=");
                }
            }
        }
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "webflux-client");
    }

}
