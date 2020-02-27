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
package co.elastic.apm.agent.plugin.api;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.bytebuddy.AnnotationValueOffsetMappingFactory;
import co.elastic.apm.agent.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_METHOD_SIGNATURE;
import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_USER_SUPPLIED;
import static co.elastic.apm.agent.plugin.api.ElasticApmApiInstrumentation.PUBLIC_API_INSTRUMENTATION_GROUP;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class TracedInstrumentation extends ElasticApmInstrumentation {

    private final StacktraceConfiguration config;

    public TracedInstrumentation(ElasticApmTracer tracer) {
        config = tracer.getConfig(StacktraceConfiguration.class);
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onMethodEnter(
        @Advice.Origin Class<?> clazz,
        @SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature String signature,
        @AnnotationValueOffsetMappingFactory.AnnotationValueExtractor(annotationClassName = "co.elastic.apm.api.Traced", method = "value") String spanName,
        @AnnotationValueOffsetMappingFactory.AnnotationValueExtractor(annotationClassName = "co.elastic.apm.api.Traced", method = "type") String type,
        @Nullable @AnnotationValueOffsetMappingFactory.AnnotationValueExtractor(annotationClassName = "co.elastic.apm.api.Traced", method = "subtype") String subtype,
        @Nullable @AnnotationValueOffsetMappingFactory.AnnotationValueExtractor(annotationClassName = "co.elastic.apm.api.Traced", method = "action") String action,
        @Advice.Local("span") AbstractSpan abstractSpan) {

        if (tracer != null) {
            final TraceContextHolder<?> parent = tracer.getActive();
            if (parent != null) {
                Span span = parent.createSpan();
                span.withType(type.isEmpty() ? "app" : type);
                span.withSubtype(subtype);
                span.withAction(action);
                span.withName(spanName.isEmpty() ? signature : spanName)
                    .activate();
                abstractSpan = span;
            } else {
                Transaction transaction = tracer.startRootTransaction(clazz.getClassLoader());
                if (spanName.isEmpty()) {
                    transaction.withName(signature, PRIO_METHOD_SIGNATURE);
                } else {
                    transaction.withName(spanName, PRIO_USER_SUPPLIED);
                }
                transaction.withType(type.isEmpty() ? Transaction.TYPE_REQUEST : type)
                    .activate();
                abstractSpan = transaction;
            }
        }

    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onMethodExit(@Nullable @Advice.Local("span") AbstractSpan span,
                                    @Advice.Thrown Throwable t) {
        if (span != null) {
            span.captureException(t)
                .deactivate();
            span.end();
        }
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return classLoaderCanLoadClass("co.elastic.apm.api.Traced");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return isInAnyPackage(config.getApplicationPackages(), ElementMatchers.<NamedElement>none())
            .and(declaresMethod(getMethodMatcher()));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isAnnotatedWith(named("co.elastic.apm.api.Traced"));
    }

    @Override
    public boolean includeWhenInstrumentationIsDisabled() {
        return false;
    }

    @Override
    public final Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList(PUBLIC_API_INSTRUMENTATION_GROUP, "annotations");
    }

}
