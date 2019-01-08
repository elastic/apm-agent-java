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
package co.elastic.apm.agent.opentracing.impl;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static co.elastic.apm.agent.opentracing.impl.ApmSpanInstrumentation.OPENTRACING_INSTRUMENTATION_GROUP;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class SpanContextInstrumentation extends ElasticApmInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.opentracing.TraceContextSpanContext");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("baggageItems");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton(OPENTRACING_INSTRUMENTATION_GROUP);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void baggageItems(@Advice.FieldValue(value = "traceContext", typing = Assigner.Typing.DYNAMIC) @Nullable TraceContext traceContext,
                                    @Advice.Return(readOnly = false) Iterable<Map.Entry<String, String>> baggage) {
        if (traceContext != null) {
            baggage = doGetBaggage(traceContext);
        }
    }

    @VisibleForAdvice
    public static Iterable<Map.Entry<String, String>> doGetBaggage(TraceContext traceContext) {
        return Collections.singletonMap(TraceContext.TRACE_PARENT_HEADER, traceContext.getOutgoingTraceParentHeader().toString()).entrySet();
    }
}
