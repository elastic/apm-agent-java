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
package co.elastic.apm.agent.finaglehttpclient;


import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import com.twitter.finagle.http.Request;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * This instrumentation targets {@link com.twitter.finagle.filter.ExceptionSourceFilter}.
 * The ExceptionSourceFilter is a very generic filter of finagle used to add the service-name to an exception
 * when a request fails.
 * We use the fact that the service-name always corresponds to the http-host in case of the HTTP client.
 * If the {@link FinaglePayloadSizeFilterInstrumentation} was not able to determine the service name, we add it here.
 */
@SuppressWarnings("JavadocReference")
public class FinagleExceptionSourceFilterInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("com.twitter.finagle.filter.ExceptionSourceFilter");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("apply")
            .and(returns(named("com.twitter.util.Future")))
            .and(takesArguments(2))
            .and(takesArgument(0, Object.class))
            .and(takesArgument(1, named("com.twitter.finagle.Service")));
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        //otherwise the FinaglePayloadSizeFilterInstrumentation.PayloadSizeFilterAdvice cannot be loaded
        return classLoaderCanLoadClass("com.twitter.finagle.http.Request$Inbound");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "finagle-httpclient");
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.finaglehttpclient.FinagleExceptionSourceFilterInstrumentation$ExceptionSourceFilterAdvice";
    }

    public static class ExceptionSourceFilterAdvice {

        private static final String SERVICE_NAME_FIELD = "com$twitter$finagle$filter$ExceptionSourceFilter$$serviceName";

        private static final Logger logger = LoggerFactory.getLogger(ExceptionSourceFilterAdvice.class);

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onBeforeExecute(@Nullable @Advice.Argument(0) Object serviceArg, @Nullable @Advice.FieldValue(SERVICE_NAME_FIELD) String serviceName) {
            if (serviceArg instanceof Request) {
                Span<?> spanToEnhance = FinaglePayloadSizeFilterInstrumentation.PayloadSizeFilterAdvice
                    .getAndRemoveSpanWithUnknownHostForRequest((Request) serviceArg);
                if (spanToEnhance != null && serviceName != null && !serviceName.isEmpty()) {
                    updateSpanHostname(spanToEnhance, serviceName);
                }

            }
        }

        private static void updateSpanHostname(Span<?> spanToEnhance, String serviceName) {
            CharSequence currentUriStr = spanToEnhance.getContext().getHttp().getUrl();
            String method = spanToEnhance.getContext().getHttp().getMethod();
            if (currentUriStr.length() == 0 || method == null || method.isEmpty()) {
                return;
            }

            try {
                URI parsed = URI.create(currentUriStr.toString());
                URI updated = new URI(parsed.getScheme().toLowerCase(Locale.US), serviceName, parsed.getPath(), parsed.getQuery(), parsed.getFragment());
                HttpClientHelper.updateHttpSpanNameAndContext(spanToEnhance, method, updated.toString(), updated.getScheme(), updated.getHost(), updated.getPort());
            } catch (Exception e) {
                logger.error("Failed to update hostname on finagle http client span", e);
            }
        }
    }
}
