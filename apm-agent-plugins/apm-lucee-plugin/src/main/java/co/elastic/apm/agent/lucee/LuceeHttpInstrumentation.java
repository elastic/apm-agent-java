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
package co.elastic.apm.agent.lucee;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;

import java.net.URL;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;

import lucee.runtime.tag.HttpParamBean;
import lucee.runtime.tag.Http;
import lucee.runtime.PageContext;
import lucee.runtime.type.KeyImpl;
import lucee.runtime.type.Struct;

public class LuceeHttpInstrumentation extends TracerAwareInstrumentation {
    // lucee.runtime.tag.Http#doEndTag
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("lucee.runtime.tag.Http").
            and(hasSuperType(named("lucee.runtime.ext.tag.BodyTagImpl")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("doEndTag");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "lucee-cfhttp");
    }

    @Override
    public String getAdviceClassName() {
        return CfHTTPAdvice.class.getName();
    }
    public static class CfHTTPAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onBeforeExecute(
                @Advice.FieldValue(value="method") @Nullable short methodId,
                @Advice.FieldValue(value="url") @Nullable String objurl,
                @Advice.FieldValue(value="params") @Nullable ArrayList<HttpParamBean> params,
                @Advice.This Http httpTag) {

            if (tracer == null || tracer.getActive() == null) {
                return null;
            }

            final AbstractSpan<?> parent = tracer.getActive();
            // Use Lucee converter as url without protocol are accepted
            URL url = null;
            Object span = null;
            try {
                url = new URL(objurl);
            }
            catch (Throwable e) {
                try {
                    url = new URL("http://" + objurl);
                } catch (Throwable seconde) {}
            }
            if (url != null) {
                span = HttpClientHelper.startHttpClientSpan(parent, getMethodName(methodId), url.toString(), url.getProtocol(), url.getHost(), url.getPort());
            } else {
                span = HttpClientHelper.startHttpClientSpan(parent, getMethodName(methodId), objurl, "unknown", "unknown", 80);
            }
            if (span != null) {
                if (params != null) {
                    TextHeaderSetter<Http> headerSetter = new LuceeHttpHeaderSetter();
                    ((Span)span).propagateTraceContext(httpTag, headerSetter);
                }
                ((Span)span).activate();
            }
            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Enter @Nullable Object span,
                                          @Advice.Thrown @Nullable Throwable t,
                                          @Advice.FieldValue(value="result") @Nullable String result,
                                          @Advice.FieldValue(value="pageContext") @Nullable PageContext pageContext) {
            if (span != null) {
                Struct resultStruct = null;
                try {
                    resultStruct = (Struct)pageContext.getVariable(result);
                } catch (Throwable e) {}
                try {
                    if (resultStruct != null) {
                        try {
                        double statusCode = (Double)(resultStruct.get(KeyImpl.intern("status_code")));
                            if (span instanceof Span) {
                                ((Span) span).getContext().getHttp().withStatusCode((int)statusCode);
                            }
                        } catch (Throwable e) {}
                    }
                    ((Span)span).captureException(t);
                } finally {
                    ((Span)span).deactivate().end();
                }
            }
        }

        public static String getMethodName(@Nullable short methodId) {
	        switch (methodId) {
                case 0: /* METHOD_GET */
                    return "GET";

                case 1: /* METHOD_POST */
                    return "POST";

                case 2: /* METHOD_HEAD */
                    return "HEAD";

                case 3: /* METHOD_PUT */
                    return "PUT";

                case 4: /* METHOD_DELETE */
                    return "DELETE";

                case 5: /* METHOD_OPTIONS */
                    return "OPTIONS";

                case 6: /* METHOD_TRACE */
                    return "TRACE";

                case 7: /* METHOD_PATCH */
                    return "PATCH";
            }
            return "UNKNOWN";
        }
    }
}
