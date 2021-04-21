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
package co.elastic.apm.agent.vertx_3_6;

import co.elastic.apm.agent.vertx_3_6.helper.VertxWebHelper;
import co.elastic.apm.agent.vertx_3_6.wrapper.ResponseEndHandlerWrapper;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

@SuppressWarnings("JavadocReference")
public abstract class Http1Instrumentation extends VertxWebInstrumentation {

    /**
     * Instruments {@link io.vertx.core.http.impl.HttpServerRequestImpl#handleBegin()} to start transaction from.
     */
    public static class HttpServerRequestImplInstrumentation extends Http1Instrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("io.vertx.core.http.impl.HttpServerRequestImpl");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("handleBegin").and(takesNoArguments());
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx_3_6.Http1RequestBeginAdvice";
        }
    }

    /**
     * Instruments {@link io.vertx.ext.web.impl.HttpServerRequestWrapper#endHandler(io.vertx.core.Handler)} to use this method with a marker-argument
     * of type {@link VertxWebHelper.NoopHandler} to unwrap the original {@link io.vertx.core.http.impl.HttpServerRequestImpl} object.
     * <p>
     * See {@link VertxWebHelper#setRouteBasedNameForCurrentTransaction(io.vertx.ext.web.RoutingContext)}.
     */
    public static class HttpServerRequestWrapperInstrumentation extends Http1Instrumentation {

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
            return "co.elastic.apm.agent.vertx_3_6.HttpServerRequestWrapperAdvice";
        }
    }

    /**
     * Instruments {@link io.vertx.core.http.impl.HttpServerRequestImpl#doEnd()} to remove the context from the context map again.
     */
    public static class HttpServerRequestEndInstrumentation extends Http1Instrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("io.vertx.core.http.impl.HttpServerRequestImpl");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("doEnd").and(takesNoArguments());
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx_3_6.Http1RequestEndAdvice";
        }
    }

    /**
     * Instruments {@link io.vertx.core.http.impl.HttpServerResponseImpl} constructor to create and append {@link ResponseEndHandlerWrapper}
     * for transaction finalization.
     */
    public static class HttpServerResponseImplInstrumentation extends Http1Instrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("io.vertx.core.http.impl.HttpServerResponseImpl");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return isConstructor();
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx_3_6.Http1ResponseConstructorAdvice";
        }
    }
}
