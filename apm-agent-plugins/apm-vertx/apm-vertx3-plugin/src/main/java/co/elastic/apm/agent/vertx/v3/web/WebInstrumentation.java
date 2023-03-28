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
package co.elastic.apm.agent.vertx.v3.web;

import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.vertx.v3.Vertx3Instrumentation;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

@SuppressWarnings("JavadocReference")
public abstract class WebInstrumentation extends Vertx3Instrumentation {

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("vertx", "vertx-web", "experimental");
    }

    /**
     * Instruments {@link io.vertx.ext.web.Route#handler(io.vertx.core.Handler)} to update transaction names based on routing information.
     */
    public static class RouteInstrumentation extends WebInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("io.vertx.ext.web.impl.RouteImpl")
                .or(named("io.vertx.ext.web.impl.RouteState"));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("handleContext")
                .and(takesArgument(0, named("io.vertx.ext.web.impl.RoutingContextImplBase")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx.v3.web.WebInstrumentation$RouteInstrumentation$RouteImplAdvice";
        }

        public static class RouteImplAdvice {

            @Nullable
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Object nextEnter(@Advice.Argument(value = 0) RoutingContext routingContext) {
                Transaction<?> transaction = WebHelper.getInstance().setRouteBasedNameForCurrentTransaction(routingContext);

                if (transaction != null) {
                    transaction.activate();
                }

                return transaction;
            }

            @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
            public static void nextExit(@Advice.Argument(value = 0) RoutingContext routingContext,
                                        @Nullable @Advice.Enter Object transactionObj, @Nullable @Advice.Thrown Throwable thrown) {
                if (transactionObj instanceof Transaction) {
                    Transaction<?> transaction = (Transaction<?>) transactionObj;
                    transaction.captureException(thrown).deactivate();
                }
            }
        }
    }

    /**
     * Instruments
     * <ul>
     *     <li>{@link io.vertx.core.http.impl.HttpServerRequestImpl#handleData(io.vertx.core.buffer.Buffer)}</li>
     *     <li>{@link io.vertx.core.http.impl.Http2ServerRequestImpl#handleData(io.vertx.core.buffer.Buffer)}</li>
     *     <li>{@code io.vertx.core.http.impl.HttpServerRequestImpl#onData(Buffer)} (since 3.9)</li>
     * </ul>
     * to handle request body capturing.
     */
    public static class RequestBufferInstrumentation extends WebInstrumentation {

        @Override
        public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
            return nameStartsWith("io.vertx.core.http.impl");
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return not(isInterface())
                .and(hasSuperType(named("io.vertx.core.http.HttpServerRequest")));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return namedOneOf("handleData", "onData")
                .and(takesArgument(0, named("io.vertx.core.buffer.Buffer")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx.v3.web.WebInstrumentation$RequestBufferInstrumentation$HandleDataAdvice";
        }

        public static class HandleDataAdvice {

            private static final WebHelper helper = WebHelper.getInstance();

            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void captureBody(@Advice.This HttpServerRequest request, @Advice.Argument(value = 0) Buffer requestDataBuffer) {
                Transaction<?> transaction = WebHelper.getInstance().getTransactionForRequest(request);
                helper.captureBody(transaction, requestDataBuffer);
            }
        }
    }
}
