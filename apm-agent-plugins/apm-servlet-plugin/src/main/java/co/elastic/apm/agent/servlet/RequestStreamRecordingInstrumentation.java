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

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.transaction.Transaction;
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

public class RequestStreamRecordingInstrumentation extends ElasticApmInstrumentation {

    @Nullable
    @VisibleForAdvice
    // referring to InputStreamWrapperFactory is legal because of type erasure
    public static HelperClassManager<InputStreamWrapperFactory> wrapperHelperClassManager;

    public RequestStreamRecordingInstrumentation(ElasticApmTracer tracer) {
        synchronized (RequestStreamRecordingInstrumentation.class) {
            if (wrapperHelperClassManager == null) {
                wrapperHelperClassManager = HelperClassManager.ForSingleClassLoader.of(tracer,
                    "co.elastic.apm.agent.servlet.helper.InputStreamFactoryHelperImpl",
                    "co.elastic.apm.agent.servlet.helper.RecordingServletInputStreamWrapper");
            }
        }
    }

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
        return named("getInputStream").and(returns(named("javax.servlet.ServletInputStream")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList(SERVLET_API, "servlet-input-stream");
    }

    @Override
    public Class<?> getAdviceClass() {
        return GetInputStreamAdvice.class;
    }

    public interface InputStreamWrapperFactory {
        ServletInputStream wrap(Request request, ServletInputStream servletInputStream);
    }

    public static class GetInputStreamAdvice {

        @VisibleForAdvice
        public static final ThreadLocal<Boolean> nestedThreadLocal = new ThreadLocal<Boolean>() {
            @Override
            protected Boolean initialValue() {
                return Boolean.FALSE;
            }
        };

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onReadEnter(@Advice.This Object thiz,
                                       @Advice.Local("transaction") Transaction transaction,
                                       @Advice.Local("nested") boolean nested) {
            nested = nestedThreadLocal.get();
            nestedThreadLocal.set(Boolean.TRUE);
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void afterGetInputStream(@Advice.Return(readOnly = false) ServletInputStream inputStream,
                                               @Advice.Local("nested") boolean nested) {
            if (nested || tracer == null || wrapperHelperClassManager == null) {
                return;
            }
            try {
                final Transaction transaction = tracer.currentTransaction();
                // only wrap if the body buffer has been initialized via ServletTransactionHelper.startCaptureBody
                if (transaction != null && transaction.getContext().getRequest().getBodyBuffer() != null) {
                    inputStream = wrapperHelperClassManager.getForClassLoaderOfClass(inputStream.getClass()).wrap(transaction.getContext().getRequest(), inputStream);
                }
            } finally {
                nestedThreadLocal.set(Boolean.FALSE);
            }
        }
    }
}
