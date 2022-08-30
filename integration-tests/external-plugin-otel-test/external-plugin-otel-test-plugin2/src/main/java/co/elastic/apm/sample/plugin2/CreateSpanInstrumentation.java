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
package co.elastic.apm.sample.plugin2;

import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class CreateSpanInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("testapp.Main");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("span");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("plugin2");
    }

    public static class AdviceClass {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter() {
            System.out.println(">> span enter");

            Tracer tracer = GlobalOpenTelemetry.get().getTracer("plugin1");
            Span span = tracer.spanBuilder("span").setSpanKind(SpanKind.INTERNAL).startSpan();
            return span.makeCurrent();
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Advice.Thrown Throwable thrown, @Advice.Enter Object scopeObject) {
            try {
                Span span = Span.current();
                try {
                    if (thrown != null) {
                        span.setStatus(StatusCode.ERROR);
                        span.recordException(thrown);
                    }
                } finally {
                    span.end();
                }
            } finally{
                Scope scope = (Scope) scopeObject;
                scope.close();
            }
            System.out.println("<< span exit");
        }
    }
}
