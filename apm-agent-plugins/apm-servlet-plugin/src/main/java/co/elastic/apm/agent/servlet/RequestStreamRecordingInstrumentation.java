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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import co.elastic.apm.agent.sdk.state.CallDepth;
import co.elastic.apm.agent.servlet.helper.RecordingServletInputStreamWrapper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import javax.servlet.ServletInputStream;
import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.servlet.ServletInstrumentation.SERVLET_API;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

public class RequestStreamRecordingInstrumentation extends AbstractServletInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Request");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("javax.servlet.ServletRequest")).and(not(isInterface()));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("getInputStream")
            .and(returns(hasSuperType(named("javax.servlet.ServletInputStream"))));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList(SERVLET_API, "servlet-input-stream");
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.servlet.RequestStreamRecordingInstrumentation$GetInputStreamAdvice";
    }

    public static class GetInputStreamAdvice {

        private static final CallDepth callDepth = CallDepth.get(GetInputStreamAdvice.class);

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onReadEnter(@Advice.This Object thiz) {
            callDepth.increment();
        }

        @Nullable
        @AssignTo.Return
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
        public static ServletInputStream afterGetInputStream(@Advice.Return @Nullable ServletInputStream inputStream) {
            if (callDepth.isNestedCallAndDecrement() || inputStream == null) {
                return inputStream;
            }
            final Transaction transaction = tracer.currentTransaction();
            // only wrap if the body buffer has been initialized via ServletTransactionHelper.startCaptureBody
            if (transaction != null && transaction.getContext().getRequest().getBodyBuffer() != null) {
                return new RecordingServletInputStreamWrapper(transaction.getContext().getRequest(), inputStream);
            } else {
                return inputStream;
            }
        }
    }
}
