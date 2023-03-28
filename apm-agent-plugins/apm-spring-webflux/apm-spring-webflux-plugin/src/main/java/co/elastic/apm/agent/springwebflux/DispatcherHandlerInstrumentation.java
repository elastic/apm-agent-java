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
package co.elastic.apm.agent.springwebflux;

import co.elastic.apm.agent.tracer.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments {@link org.springframework.web.reactive.DispatcherHandler#handle(ServerWebExchange)} that handles both
 * transaction creation and lifecycle through wrapping. This is used for both annotation-based and functional variants.
 */
public class DispatcherHandlerInstrumentation extends WebFluxInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.springframework.web.reactive.DispatcherHandler");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("handle")
            .and(takesArgument(0, named("org.springframework.web.server.ServerWebExchange")));
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.springwebflux.DispatcherHandlerInstrumentation$HandleAdvice";
    }

    public static class HandleAdvice {

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.Argument(0) ServerWebExchange exchange) {

            List<String> upgradeHeader = exchange.getRequest().getHeaders().get("upgrade");
            if (upgradeHeader != null && upgradeHeader.contains("websocket")) {
                // just ignore upgrade WS upgrade requests for now
                return null;
            }
            return WebfluxHelper.getOrCreateTransaction(tracer, exchange);
        }

        @Nullable
        @Advice.AssignReturned.ToReturned(typing = Assigner.Typing.DYNAMIC) // required to provide the Mono<?> return value type
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static Object onExit(@Advice.Enter @Nullable Object enterTransaction,
                                    @Advice.Argument(0) ServerWebExchange exchange,
                                    @Advice.Thrown @Nullable Throwable thrown,
                                    @Advice.Return @Nullable Mono<?> returnValue) {

            if (!(enterTransaction instanceof Transaction)) {
                return returnValue;
            }
            Transaction<?> transaction = (Transaction<?>) enterTransaction;
            transaction.deactivate();

            if (thrown != null || returnValue == null) {
                // in case of thrown exception, we don't need to wrap to end transaction
                return returnValue;
            }

            return WebfluxHelper.wrapDispatcher(returnValue, transaction, exchange);
        }
    }

}
