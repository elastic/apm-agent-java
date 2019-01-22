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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class RequestDispatcherInstrumentation extends ElasticApmInstrumentation {

    private static final String SPAN_TYPE_REQUEST_DISPATCHER = "servlet.request-dispatcher.forward";
    private static final String FORWARD_INCLUDE = "forward-include";

    @Override
    public Class<?> getAdviceClass() {
        return RequestDispatcherAdvice.class;
    }

    @VisibleForAdvice
    public static class RequestDispatcherAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void beforeExecute(@Advice.Local("transaction") @Nullable Transaction transaction,
                                         @Advice.Local("span") @Nullable Span span) {
            System.out.println("Tracer ..");
            if (tracer == null || tracer.getActive() == null) {
                System.out.println("tracer is null");
                return;
            }
            System.out.println("Trying to activate span");
            final TraceContextHolder<?> parent = tracer.getActive();
            span = parent.createSpan().withType(SPAN_TYPE_REQUEST_DISPATCHER).withName(FORWARD_INCLUDE).withName(" ");
            span.activate();
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void afterExecute(@Advice.Local("span") @Nullable Span span,
                                         @Advice.Thrown @Nullable Throwable t) {
            if (span != null) {
                span.captureException(t)
                    .deactivate()
                    .end();
            }
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameContains("dispatcher");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("include")
            .or(named("forward"));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("servlet", "request-dispatcher");
    }
}
