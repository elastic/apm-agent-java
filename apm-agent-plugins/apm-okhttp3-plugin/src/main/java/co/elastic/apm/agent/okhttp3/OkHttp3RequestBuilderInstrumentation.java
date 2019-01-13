/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.okhttp3;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class OkHttp3RequestBuilderInstrumentation extends ElasticApmInstrumentation {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    private static void onBeforeExecute(@Advice.This @Nullable Object thiz,
                                        @Advice.FieldValue(value = "headers", typing = Assigner.Typing.STATIC) @Nullable Object headers,
                                        @Advice.Local("span") Span span) {
        if (tracer == null || tracer.activeSpan() == null) {
            return;
        }
        final AbstractSpan<?> parent = tracer.activeSpan();
        System.out.println("Trying to add headers...");

        if (thiz instanceof com.squareup.okhttp.Request.Builder) {
            System.out.println("Headers.okhttp");
            if (headers instanceof com.squareup.okhttp.Headers.Builder) {
                if (parent != null) {
                    ((com.squareup.okhttp.Headers.Builder) headers).set(TraceContext.TRACE_PARENT_HEADER, parent.getTraceContext().getOutgoingTraceParentHeader().toString());
                }
            }
        }

        if (thiz instanceof okhttp3.Request.Builder) {
            System.out.println("Headers.okhttp3:"+parent.getTraceContext().getOutgoingTraceParentHeader().toString());
            if (headers instanceof okhttp3.Headers.Builder) {
                if (parent != null) {
                    ((okhttp3.Headers.Builder) headers).set(TraceContext.TRACE_PARENT_HEADER, parent.getTraceContext().getOutgoingTraceParentHeader().toString());
                }
            }
        }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onAfterExecute(@Advice.Local("span") @Nullable Span span,
                                      @Advice.Thrown @Nullable Throwable t) {
        System.out.println("Headers.after");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("com.squareup.okhttp.Request$Builder")
            .or(named("okhttp3.Request$Builder"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("build");
    }


    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "okhttp", "request-builder");
    }

}
