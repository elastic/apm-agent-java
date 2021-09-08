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
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.AbstractHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import java.util.Collection;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Map;

import lucee.runtime.type.Struct;
import lucee.runtime.PageContext;

public class LuceeInternalRequestInstrumentation extends TracerAwareInstrumentation {

    private static final String INTERNAL_REQUEST = "internalRequest";
    private static final String LUCEE_RUNTIME_TYPE_STRUCT = "lucee.runtime.type.Struct";

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("lucee.runtime.functls ions.system.InternalRequest"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("call")
            .and(takesArguments(10))
            .and(takesArgument(0, named("lucee.runtime.PageContext")))
            .and(takesArgument(1, String.class))
            .and(takesArgument(2, String.class))
            .and(takesArgument(3, named(LUCEE_RUNTIME_TYPE_STRUCT)))
            .and(takesArgument(4, named(LUCEE_RUNTIME_TYPE_STRUCT)))
            .and(takesArgument(5, named(LUCEE_RUNTIME_TYPE_STRUCT)))
            .and(takesArgument(6, named(LUCEE_RUNTIME_TYPE_STRUCT)))
            .and(takesArgument(7, Object.class))
            .and(takesArgument(8, String.class))
            .and(takesArgument(9, boolean.class))
            .and(returns(named(LUCEE_RUNTIME_TYPE_STRUCT)));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList(INTERNAL_REQUEST);
    }

    @Override
    public String getAdviceClassName() {
        return CfInternalRequestAdvice.class.getName();
    }

    public static class CfInternalRequestAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onBeforeExecute(
            @Advice.Origin Class<?> clazz,
            @Advice.Argument(value=0) @Nullable PageContext pc,
            @Advice.Argument(value=1) @Nullable String template,
            @Advice.Argument(value=2) @Nullable String method,
            @Advice.Argument(value=3) @Nullable Struct urls,
            @Advice.Argument(value=4) @Nullable Struct forms,
            @Advice.Argument(value=5) @Nullable Struct cookies,
            @Advice.Argument(value=6) @Nullable Struct headers,
            @Advice.Argument(value=7) @Nullable Object body,
            @Advice.Argument(value=8) @Nullable String strCharset,
            @Advice.Argument(value=9) @Nullable boolean addToken
            ) {

            if (tracer == null || tracer.getActive() == null || pc == null || template == null || method == null) {
                return null;
            }

            final AbstractSpan<?> parent = tracer.getActive();
            final AbstractSpan<?> currentTransaction = tracer.currentTransaction();
            AbstractSpan<?> span = parent.createSpan()
                .withName("internalRequest " + method + " " +template)
                .withType("lucee")
                .withSubtype(INTERNAL_REQUEST)
                .withAction("call");
            if (span != null) {
                ((Span)span).activate();
            } else {
                return null;
            }

            Map<String, String> headersMap = new HashMap<>();
            span.propagateTraceContext(headersMap, new LuceeIRHeaderSetter());

            ((Span)span).deactivate();
            ((Transaction)currentTransaction).deactivate();

            final Transaction childTransaction = tracer.startChildTransaction(headersMap, new LuceeIRHeaderGetter(), clazz.getClassLoader());

            if (childTransaction != null) {
                childTransaction.withName(method + " " + template);
                childTransaction.setFrameworkName("Lucee");
                childTransaction.withType(INTERNAL_REQUEST);
                childTransaction.activate();
            } else {
                ((Transaction)currentTransaction).activate();
                ((Span)span).activate();
            }
            AbstractSpan<?>[] retval = new AbstractSpan[3];
            retval[0] = childTransaction;
            retval[1] = span;
            retval[2] = currentTransaction;
            return retval;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Enter @Nullable Object arrayOfSpan,
                                          @Advice.Thrown @Nullable Throwable t) {
            if (arrayOfSpan != null) {
                try {
                    ((Transaction)((AbstractSpan<?>[])arrayOfSpan)[0]).captureException(t);
                    ((Span)((AbstractSpan<?>[])arrayOfSpan)[1]).captureException(t);
                } finally {
                    ((Transaction)((AbstractSpan<?>[])arrayOfSpan)[0]).deactivate().end();
                    ((Transaction)((AbstractSpan<?>[])arrayOfSpan)[2]).activate();
                    ((Span)((AbstractSpan<?>[])arrayOfSpan)[1]).activate(); // To make sure we end with the span active
                    ((Span)((AbstractSpan<?>[])arrayOfSpan)[1]).deactivate().end();
                }
            }
        }

        private static class LuceeIRHeaderSetter implements TextHeaderSetter<Map<String, String>> {

            @Override
            public void setHeader(String headerName, String headerValue, Map<String, String> headersMap) {
                headersMap.put(headerName, headerValue);
            }
        }

        private static class LuceeIRHeaderGetter extends AbstractHeaderGetter<String, Map<String, String>> implements TextHeaderGetter<Map<String, String>> {

            @Nullable
            @Override
            public String getFirstHeader(String headerName, Map<String, String> headersMap) {
                return headersMap.get(headerName);
            }

        }

    }
}
