/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.lucee;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import org.apache.http.client.methods.HttpRequestBase;
import java.net.URI;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;

import lucee.runtime.tag.HttpParamBean;
import lucee.runtime.tag.Http;
import lucee.runtime.PageContext;
import lucee.runtime.type.KeyImpl;
import lucee.runtime.type.Struct;
import lucee.commons.net.HTTPUtil;

import co.elastic.apm.agent.lucee.LuceeHttpHeaderSetter;

public class LuceeHttpInstrumentation extends ElasticApmInstrumentation {

    // We can refer OkHttp types thanks to type erasure
    @VisibleForAdvice
    @Nullable
    public static HelperClassManager<TextHeaderSetter<Http>> headerSetterHelperManager;

    public LuceeHttpInstrumentation(ElasticApmTracer tracer) {
        synchronized (LuceeHttpHeaderSetter.class) {
            if (headerSetterHelperManager == null) {
                headerSetterHelperManager = HelperClassManager.ForAnyClassLoader.of(tracer,
                    "co.elastic.apm.agent.lucee.LuceeHttpHeaderSetter"
                );
            }
        }
    }

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
    public Class<?> getAdviceClass() {
        return CfHTTPAdvice.class;
    }
    @VisibleForAdvice
    public static class CfHTTPAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onBeforeExecute(
                @Advice.FieldValue(value="method") @Nullable short methodId,
                @Advice.FieldValue(value="url") @Nullable String objurl,
                @Advice.FieldValue(value="params", readOnly=false) @Nullable  ArrayList<HttpParamBean> params,
                @Advice.This Http httpTag,
                @Advice.Local("span") Span span) {

            if (tracer == null || tracer.getActive() == null) {
                return;
            }

            final AbstractSpan<?> parent = tracer.getActive();
            // Use Lucee converter as url without protocol are accepted
            URI url = HTTPUtil.toURL(objurl);
            span = HttpClientHelper.startHttpClientSpan(parent, getMethodName(methodId), url.toString(), url.getScheme(), url.getHost(), url.getPort());
            if (span != null) {
                if (params != null) {
                    TextHeaderSetter<Http> headerSetter = headerSetterHelperManager.getForClassLoaderOfClass(Http.class);
                    span.propagateTraceContext(httpTag, headerSetter);
                }
                span.activate();
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onAfterExecute(@Advice.Local("span") @Nullable Span span,
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
                        span.getContext().getHttp().withStatusCode(((int)statusCode));
                        } catch (Throwable e) {}
                    }
                    span.captureException(t);
                } finally {
                    span.deactivate().end();
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
