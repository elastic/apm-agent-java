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

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
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
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.util.Collection;
import java.util.Arrays;
import lucee.runtime.PageSource;

public class LuceeApplicationInstrumentation extends TracerAwareInstrumentation {
// lucee.runtime.listener.ModernAppListener#call(Component app, PageContext pc, Collection.Key eventName, Object[] args, boolean catchAbort) throws PageException {

    // lucee.runtime.listener.ModernAppListener#call
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("lucee.runtime.listener.ModernAppListener");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("call").
            and(takesArguments(5));
    }

    @Override
    public java.util.Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("lucee", "application");
    }

    @Override
    public Class<?> getAdviceClass() {
        return CfCompileAdvice.class;
    }
    @VisibleForAdvice
    public static class CfCompileAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onBeforeExecute(
                @Advice.Argument(value=2) @Nullable lucee.runtime.type.Collection.Key eventName) {

            if (eventName == null) {
                return null;
            }
            if (tracer == null || tracer.getActive() == null) {
                return null;
            }
            String eventStringName = eventName.getString();
            // Skip, the main "body" event, as it's basically the full request, skip it to use a full span
            if (eventStringName == "onRequest") {
                return null;
            }
            final AbstractSpan<?> parent = tracer.getActive();
            Object span = parent.createSpan()
                    .withName(eventStringName)
                    .withType("lucee")
                    .withSubtype(eventStringName)
                    .withAction("hook");
            if (span != null) {
                ((Span)span).activate();
            }
            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Enter @Nullable Object span,
                                          @Advice.Thrown @Nullable Throwable t) {
            if (span != null) {
                try {
                    ((Span)span).captureException(t);
                } finally {
                    ((Span)span).deactivate().end();
                }
            }
        }
    }
}
/*
lucee.runtime.listener.ModernAppListener#call
lucee.runtime.type.Collection.Key
	private Object call(Component app, PageContext pc, Collection.Key eventName, Object[] args, boolean catchAbort) throws PageException {
*/
