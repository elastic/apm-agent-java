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
package co.elastic.apm.agent.vertx_4;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

public abstract class VertxWebInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        // ensure only Vertx versions >= 4.0 are instrumented
        return classLoaderCanLoadClass("io.vertx.core.http.impl.Http1xServerRequest");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("vertx", "vertx-web");
    }

    /**
     * Instruments {@link io.vertx.core.impl.ContextImpl#tracer}} to return a noop tracer in case no tracer has been specified.
     */
    public static class ContextImplTracerInstrumentation extends VertxWebInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("io.vertx.core.impl.ContextImpl").and(not(isInterface()));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("tracer").and(takesNoArguments());
        }

        @Override
        public Class<?> getAdviceClass() {
            return ContextImplTracerAdvice.class;
        }
    }

    /**
     * Instruments {@link io.vertx.core.spi.tracing.VertxTracer#receiveRequest}} to intercept tracer calls.
     */
    public static class VertxTracerReceiveRequestInstrumentation extends VertxWebInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return hasSuperType(named("io.vertx.core.spi.tracing.VertxTracer")).and(not(isInterface()));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("receiveRequest").and(takesArgument(0, named("io.vertx.core.Context")));
        }

        @Override
        public Class<?> getAdviceClass() {
            return TracerReceiveRequestAdvice.class;
        }
    }

    /**
     * Instruments {@link io.vertx.core.spi.tracing.VertxTracer#sendResponse}} to intercept tracer calls.
     */
    public static class VertxTracerSendResponseInstrumentation extends VertxWebInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return hasSuperType(named("io.vertx.core.spi.tracing.VertxTracer")).and(not(isInterface()));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("sendResponse").and(takesArgument(0, named("io.vertx.core.Context")));
        }

        @Override
        public Class<?> getAdviceClass() {
            return TracerSendResponseAdvice.class;
        }
    }

    /**
     * Instruments {@link io.vertx.ext.web.Route#handler} to wrap router executions for better transaction naming based on routing information.
     */
    public static class RouteInstrumentation extends VertxWebInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return not(isInterface()).and(named("io.vertx.ext.web.impl.RouteState"));
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
     * Instruments
     * <ul>
     *     <li>{@link io.vertx.core.http.impl.Http1xServerRequest#onData}</li>
     *     <li>{@link io.vertx.core.http.impl.Http2ServerRequestImpl#handleData}</li>
     * </ul>
     * <p>
     * to enable request body capturing.
     */
    public static class RequestBufferInstrumentation extends VertxWebInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return not(isInterface()).and(hasSuperType(named("io.vertx.core.http.HttpServerRequest")));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return namedOneOf("onData", "handleData").and(takesArgument(0, named("io.vertx.core.buffer.Buffer")));
        }

        @Override
        public Class<?> getAdviceClass() {
            return HandleDataAdvice.class;
        }
    }

}
