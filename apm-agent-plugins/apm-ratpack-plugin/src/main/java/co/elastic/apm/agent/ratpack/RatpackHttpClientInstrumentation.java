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
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.exec.Promise;
import ratpack.func.Action;
import ratpack.http.client.HttpClient;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;

import javax.annotation.Nullable;
import java.net.URI;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class RatpackHttpClientInstrumentation extends AbstractRatpackInstrumentation {

    @SuppressWarnings("WeakerAccess")
    @VisibleForAdvice
    @Nullable
    public static HelperClassManager<HttpClientHelper<Action<? super RequestSpec>, Promise<? extends HttpResponse>>> helperManager;

    public RatpackHttpClientInstrumentation(final ElasticApmTracer tracer) {
        if (helperManager == null) {
            helperManager = HelperClassManager.ForSingleClassLoader.of(tracer,
                "co.elastic.apm.agent.ratpack.RatpackHttpClientHelperImpl",
                "co.elastic.apm.agent.ratpack.RatpackHttpClientHelperImpl$RequestAction",
                "co.elastic.apm.agent.ratpack.RatpackHttpClientHelperImpl$ErrorAction",
                "co.elastic.apm.agent.ratpack.RatpackHttpClientHelperImpl$ResponseAction");
        }
    }

    @Override
    public final ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named("ratpack.http.client.HttpClient")));
    }

    @Override
    public final ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isPublic()
            .and(named("request").or(named("requestStream")))
            .and(takesArgument(0, URI.class))
            .and(takesArgument(1, named("ratpack.func.Action")))
            .and(returns(named("ratpack.exec.Promise")));
    }

    @Override
    public Class<?> getAdviceClass() {
        return RatpackHttpClientAdvice.class;
    }

    public interface HttpClientHelper<ARS, PHR> {

        ARS startHttpClientSpan(Span span, URI uri, ARS action);

        PHR endHttpClientSpan(Span span, PHR response);
    }

    @VisibleForAdvice
    public static class RatpackHttpClientAdvice {

        @SuppressWarnings("WeakerAccess")
        public static final Logger logger = LoggerFactory.getLogger(RatpackHttpClientAdvice.class);

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onBeforeRequest(
            @Advice.Argument(value = 0) final URI uri,
            @Advice.Argument(value = 1, readOnly = false) Action<? super RequestSpec> action,
            @SuppressWarnings("ParameterCanBeLocal")
            @Advice.Local("span") Span span,
            @SuppressWarnings("ParameterCanBeLocal")
            @Advice.Local("helper") HttpClientHelper<Action<? super RequestSpec>, Promise<? extends HttpResponse>> helper
        ) {

            if (tracer == null || tracer.getActive() == null || helperManager == null) {
                return;
            }

            helper = helperManager.getForClassLoaderOfClass(HttpClient.class);
            final TraceContextHolder<?> parent = tracer.getActive();

            if (parent == null || helper == null) {
                return;
            }

            span = parent.createExitSpan();

            if (span != null) {
                logger.debug("Activating span [{}] for request [{}].", span, uri);
                span.activate();
                //noinspection UnusedAssignment
                action = helper.startHttpClientSpan(span, uri, action);
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onAfterRequest(
            @Advice.Local("span") @Nullable Span span,
            @Advice.Thrown @Nullable Throwable throwable,
            @Advice.Return(readOnly = false) Promise<? extends HttpResponse> response,
            @Advice.Local("helper") @Nullable HttpClientHelper<Action<? super RequestSpec>, Promise<? extends HttpResponse>> helper
        ) {
            // logic in OnMethodEnter helper == null implies span == null
            if (span == null || helper == null) {
                return;
            }

            logger.debug("De-activating span [{}].", span);

            span.deactivate();

            if (throwable != null) {
                span.captureException(throwable).end();
            } else {
                //noinspection UnusedAssignment
                response = helper.endHttpClientSpan(span, response);
            }
        }
    }
}
