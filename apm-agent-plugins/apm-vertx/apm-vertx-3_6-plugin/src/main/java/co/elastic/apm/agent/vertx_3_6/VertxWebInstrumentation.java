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

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.vertx_3_6.wrapper.ResponseEndHandlerWrapper;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.declaresField;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public abstract class VertxWebInstrumentation extends TracerAwareInstrumentation {

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("vertx", "vertx-web");
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        // ensure only Vertx versions >= 3.6 and < 4.0 are instrumented
        return classLoaderCanLoadClass("io.vertx.core.http.impl.HttpServerRequestImpl")
                .and(not(classLoaderCanLoadClass("io.vertx.core.impl.Action")));
    }

    /**
     * Instruments {@link io.vertx.ext.web.Route#handler(io.vertx.core.Handler)} to update transaction names based on routing information.
     */
    public static class RouteInstrumentation extends VertxWebInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return not(isInterface()).and(named("io.vertx.ext.web.impl.RouteImpl").or(named("io.vertx.ext.web.impl.RouteState")));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("handleContext").and(takesArgument(0, named("io.vertx.ext.web.impl.RoutingContextImplBase")));
        }

        @Override
        public Class<?> getAdviceClass() {
            return RouteImplAdvice.class;
        }
    }

    /**
     * Instruments {@link io.vertx.core.http.HttpServerResponse#endHandler(io.vertx.core.Handler)} to handle proper wrapping of existing end
     * handlers when adding our {@link ResponseEndHandlerWrapper} for transaction finalization.
     */
    public static class ResponseEndHandlerInstrumentation extends VertxWebInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return not(isInterface()).and(hasSuperType(named("io.vertx.core.http.HttpServerResponse"))).and(declaresField(named("endHandler")));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("endHandler").and(takesArgument(0, named("io.vertx.core.Handler")));
        }

        @Override
        public Class<?> getAdviceClass() {
            return ResponseEndHandlerAdvice.class;
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
    public static class RequestBufferInstrumentation extends VertxWebInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return not(isInterface()).and(hasSuperType(named("io.vertx.core.http.HttpServerRequest")));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return namedOneOf("handleData", "onData").and(takesArgument(0, named("io.vertx.core.buffer.Buffer")));
        }

        @Override
        public Class<?> getAdviceClass() {
            return HandleDataAdvice.class;
        }
    }

}
