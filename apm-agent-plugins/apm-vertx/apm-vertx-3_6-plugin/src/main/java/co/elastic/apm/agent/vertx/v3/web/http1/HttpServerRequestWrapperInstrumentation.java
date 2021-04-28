/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.vertx.v3.web.http1;

import co.elastic.apm.agent.sdk.advice.AssignTo;
import co.elastic.apm.agent.vertx.v3.web.WebHelper;
import co.elastic.apm.agent.vertx.v3.web.WebInstrumentation;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments {@link io.vertx.ext.web.impl.HttpServerRequestWrapper#endHandler(io.vertx.core.Handler)} to use this method with a marker-argument
 * of type {@link WebHelper.NoopHandler} to unwrap the original {@link io.vertx.core.http.impl.HttpServerRequestImpl} object.
 * <p>
 * See {@link WebHelper#setRouteBasedNameForCurrentTransaction(io.vertx.ext.web.RoutingContext)}.
 */
@SuppressWarnings("JavadocReference")
public class HttpServerRequestWrapperInstrumentation extends WebInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.vertx.ext.web.impl.HttpServerRequestWrapper");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("endHandler")
            .and(takesArgument(0, named("io.vertx.core.Handler")))
            .and(returns(named("io.vertx.core.http.HttpServerRequest")));
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.vertx.v3.web.http1.HttpServerRequestWrapperInstrumentation$HttpServerRequestWrapperAdvice";
    }

    public static class HttpServerRequestWrapperAdvice {

        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class, inline = false)
        public static boolean enter(@Advice.Argument(0) Handler<Void> handler) {
            return handler instanceof WebHelper.NoopHandler;
        }

        @AssignTo.Return
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static HttpServerRequest exit(@Advice.FieldValue(value = "delegate") HttpServerRequest delegate) {
            return delegate;
        }
    }
}
