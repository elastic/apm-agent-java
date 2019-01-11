/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.conn.routing.HttpRoute;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.http.client.HttpClientHelper.HTTP_CLIENT_SPAN_TYPE_PREFIX;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ApacheHttpClientInstrumentation extends ElasticApmInstrumentation {

    private static final String SPAN_TYPE_APACHE_HTTP_CLIENT = HTTP_CLIENT_SPAN_TYPE_PREFIX + "apache-httpclient";

    @Advice.OnMethodEnter(suppress = Throwable.class)
    private static void onBeforeExecute(@Advice.Argument(0) HttpRoute route,
                                        @Advice.Argument(1) HttpRequestWrapper request,
                                        @Advice.Local("span") Span span) {
        if (tracer == null || tracer.getActive() == null) {
            return;
        }
        final TraceContextHolder<?> parent = tracer.getActive();
        span = HttpClientHelper.startHttpClientSpan(parent, request.getMethod(), request.getURI(), route.getTargetHost().getHostName(), SPAN_TYPE_APACHE_HTTP_CLIENT);
        if (span != null) {
            request.addHeader(TraceContext.TRACE_PARENT_HEADER, span.getTraceContext().getOutgoingTraceParentHeader().toString());
        } else if (!request.containsHeader(TraceContext.TRACE_PARENT_HEADER) && parent != null) {
            // re-adds the header on redirects
            request.addHeader(TraceContext.TRACE_PARENT_HEADER, parent.getTraceContext().getOutgoingTraceParentHeader().toString());
        }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onAfterExecute(@Advice.Return CloseableHttpResponse response,
                                      @Advice.Local("span") @Nullable Span span,
                                      @Advice.Thrown @Nullable Throwable t) {
        if (span != null) {
            span.captureException(t)
                .deactivate()
                .end();
        }
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Exec").or(nameContains("Chain"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.apache.http.impl.execchain.ClientExecChain"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute")
            .and(takesArguments(4))
            .and(returns(hasSuperType(named("org.apache.http.client.methods.CloseableHttpResponse"))))
            .and(takesArgument(0, hasSuperType(named("org.apache.http.conn.routing.HttpRoute"))))
            .and(takesArgument(1, hasSuperType(named("org.apache.http.client.methods.HttpRequestWrapper"))))
            .and(takesArgument(2, hasSuperType(named("org.apache.http.client.protocol.HttpClientContext"))))
            .and(takesArgument(3, hasSuperType(named("org.apache.http.client.methods.HttpExecutionAware"))));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "apache-httpclient");
    }
}
