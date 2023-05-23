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
package co.elastic.apm.agent.httpclient.v3;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.dispatch.HeaderUtils;
import co.elastic.apm.agent.util.LoggerUtils;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.commons.httpclient.CircularRedirectException;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.StatusLine;
import org.apache.commons.httpclient.URI;

import javax.annotation.Nullable;

import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments {@link org.apache.commons.httpclient.HttpMethodDirector#executeMethod(HttpMethod)}
 */
@SuppressWarnings("JavadocReference") // instrumented class is package-private
public class HttpClient3Instrumentation extends TracerAwareInstrumentation {

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "apache-httpclient");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.apache.commons.httpclient.HttpMethodDirector");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("executeMethod");
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.httpclient.v3.HttpClient3Instrumentation$HttpClient3Advice";
    }

    public static class HttpClient3Advice {

        private static final Logger oneTimeNoDestinationInfoLogger;

        static {
            oneTimeNoDestinationInfoLogger = LoggerUtils.logOnce(LoggerFactory.getLogger("Apache-HttpClient-3-Destination"));
        }

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.Argument(0) HttpMethod httpMethod,
                                     @Advice.FieldValue(value = "hostConfiguration") HostConfiguration hostConfiguration) {
            final AbstractSpan<?> parent = TracerAwareInstrumentation.tracer.getActive();
            if (parent == null) {
                return null;
            }

            String host;
            String uri;
            String protocol;
            int port;
            URI httpClientURI = null;
            try {
                httpClientURI = httpMethod.getURI();
                host = hostConfiguration.getHost();
                port = hostConfiguration.getPort();
                protocol = hostConfiguration.getProtocol().getScheme();
                uri = httpClientURI.toString();
            } catch (Exception e) {
                try {
                    if (httpClientURI != null) {
                        host = httpClientURI.getHost();
                        uri = httpClientURI.toString();
                        protocol = httpClientURI.getScheme();
                        port = httpClientURI.getPort();
                    } else {
                        oneTimeNoDestinationInfoLogger.warn("Failed to obtain Apache HttpClient destination info, null httpClientURI", e);
                        return null;
                    }
                } catch (Exception e1) {
                    oneTimeNoDestinationInfoLogger.warn("Failed to obtain Apache HttpClient destination info", e);
                    return null;
                }
            }

            Span<?> span = HttpClientHelper.startHttpClientSpan(parent, httpMethod.getName(), uri, protocol, host, port);

            if (span != null) {
                span.activate();
            }

            if (!HeaderUtils.containsAny(tracer.getTraceHeaderNames(), httpMethod, HttpClient3RequestHeaderAccessor.INSTANCE)) {
                if (span != null) {
                    span.propagateTraceContext(httpMethod, HttpClient3RequestHeaderAccessor.INSTANCE);
                } else if (!HeaderUtils.containsAny(tracer.getTraceHeaderNames(), httpMethod, HttpClient3RequestHeaderAccessor.INSTANCE)) {
                    // re-adds the header on redirects
                    parent.propagateTraceContext(httpMethod, HttpClient3RequestHeaderAccessor.INSTANCE);
                }
            }

            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Advice.Thrown @Nullable Throwable thrown,
                                  @Advice.Argument(0) HttpMethod httpMethod,
                                  @Advice.Enter @Nullable Object enterSpan) {

            if (!(enterSpan instanceof Span<?>)) {
                return;
            }

            Span<?> span = (Span<?>) enterSpan;

            StatusLine statusLine = httpMethod.getStatusLine();
            if (null != statusLine) {
                span.getContext().getHttp().withStatusCode(statusLine.getStatusCode());
            }

            if (thrown instanceof CircularRedirectException) {
                span.withOutcome(Outcome.FAILURE);
            }

            span.captureException(thrown)
                .deactivate()
                .end();

        }
    }
}
