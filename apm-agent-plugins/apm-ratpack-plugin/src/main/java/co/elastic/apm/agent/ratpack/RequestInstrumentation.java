/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.ratpack;

import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import io.netty.buffer.ByteBuf;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import ratpack.exec.Promise;
import ratpack.func.Block;
import ratpack.http.Request;
import ratpack.http.TypedData;
import ratpack.stream.TransformablePublisher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

/**
 * Instrumentation to capture the http request body into the transaction.
 *
 * The instrumentation matches the getBody and getBodyStream contracts. It only instruments the returned promise object,
 * and doesn't care about what parameters are passed to these methods. The body will not be captures if the promise is
 * not evaluated.
 *
 * @see Request#getBody()
 * @see Request#getBody(Block)
 * @see Request#getBody(long)
 * @see Request#getBody(long, Block)
 * @see Request#getBodyStream()
 * @see Request#getBodyStream(long)
 */
@SuppressWarnings("WeakerAccess")
public abstract class RequestInstrumentation extends AbstractRatpackInstrumentation {

    @VisibleForAdvice
    @Nullable
    public static HelperClassManager<RequestHelper<Request, Promise<TypedData>, TransformablePublisher<? extends ByteBuf>>> helperManager;

    private final ElementMatcher<? super MethodDescription> methodMatcher;

    RequestInstrumentation(final ElasticApmTracer tracer, final ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
        if (helperManager == null) {
            helperManager = HelperClassManager.ForSingleClassLoader.of(tracer,
                "co.elastic.apm.agent.ratpack.RequestHelperImpl",
                "co.elastic.apm.agent.ratpack.TransactionHolderImpl",
                "co.elastic.apm.agent.ratpack.RequestHelperImpl$BodyWiretapper",
                "co.elastic.apm.agent.ratpack.RequestHelperImpl$BodyWiretapper$CaptureBodyFunction",
                "co.elastic.apm.agent.ratpack.RequestHelperImpl$BodyWiretapper$CaptureBodyAction",
                "co.elastic.apm.agent.ratpack.RequestHelperImpl$BodyWiretapper$CaptureStreamFunction",
                "co.elastic.apm.agent.ratpack.RequestHelperImpl$BodyWiretapper$CaptureStreamAction",
                "co.elastic.apm.agent.ratpack.RequestHelperImpl$AppendableOutputStream" );
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named("ratpack.http.Request")));
    }

    @Override
    public final ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }

    public interface RequestHelper<R, P, T> {

        P addBodyWiretapIfCapturing(Request request, P promise);

        T addStreamWiretapIfCapturing(R request, T publisher);
    }

    public static class RatpackReadBodyInstrumentation extends RequestInstrumentation {


        public RatpackReadBodyInstrumentation(final ElasticApmTracer tracer) {
            super(tracer, named("getBody")
                .and(isPublic())
                .and(returns(named("ratpack.exec.Promise")))
            );
        }

        @Override
        public Class<?> getAdviceClass() {
            return RatpackReadBodyAdvice.class;
        }

        @VisibleForAdvice
        public static class RatpackReadBodyAdvice {

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
            public static void onAfterGetBody(
                @Advice.Return(readOnly = false) Promise<TypedData> promise,
                @Advice.This ratpack.http.Request request) {

                if (tracer == null || helperManager == null) {
                    return;
                }

                final RequestHelper<Request, Promise<TypedData>, TransformablePublisher<? extends ByteBuf>> helper = helperManager.getForClassLoaderOfClass(Request.class);

                if (helper != null) {
                    //noinspection UnusedAssignment
                    promise = helper.addBodyWiretapIfCapturing(request, promise);
                }
            }
        }
    }

    public static class RatpackReadStreamInstrumentation extends RequestInstrumentation {

        public RatpackReadStreamInstrumentation(final ElasticApmTracer tracer) {
            super(tracer, named("getBodyStream")
                .and(isPublic())
                .and(returns(named("ratpack.stream.TransformablePublisher")))
            );
        }

        @Override
        public Class<?> getAdviceClass() {
            return RatpackReadStreamAdvice.class;
        }

        @VisibleForAdvice
        public static class RatpackReadStreamAdvice {

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
            public static void onAfterGetBodyStream(
                @Advice.Return(readOnly = false) TransformablePublisher<? extends ByteBuf> publisher,
                @Advice.This ratpack.http.Request request
            ) {

                if (tracer == null || helperManager == null) {
                    return;
                }

                final RequestHelper<Request, Promise<TypedData>, TransformablePublisher<? extends ByteBuf>> helper = helperManager.getForClassLoaderOfClass(Request.class);

                if (helper != null) {

                    //noinspection UnusedAssignment
                    publisher = helper.addStreamWiretapIfCapturing(request, publisher);
                }
            }
        }
    }
}
