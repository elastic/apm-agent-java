/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.cdi;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isEquals;
import static net.bytebuddy.matcher.ElementMatchers.isHashCode;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.isToString;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * Looks for all classes Annotated with any CDI scope annotation and creates a span for each call of a public method on those classes.
 * Only classes within applicationPackages are considered.
 *
 * A minimum run times for methods may be defined via the {@link CdiConfiguration#getTraceMethodsDurationThreshold()} option
 * to filter out short running methods.
 */
public class CdiInstrumentation extends ElasticApmInstrumentation {

    private Collection<String> applicationPackages;

    @SuppressWarnings("WeakerAccess") // must be public to be able to access it from instrumented methods
    public static long cdiMethodThresholdMicros;

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onMethodEnter(@SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature String signature,
                                     @Advice.Local("span") AbstractSpan<?> span) {
        if (tracer != null) {
            final TraceContextHolder<?> parent = tracer.getActive();
            if (parent != null) {
                span = parent.createSpan()
                    .withName(signature)
                    .withType("cdi")
                    .activate();
                // by default discard such spans
                span.setDiscard(true);
            }
        }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onMethodExit(@Nullable @Advice.Local("span") AbstractSpan<?> span,
                                    @Advice.Thrown Throwable t) {
        if (span != null) {
            span.captureException(t);
            final long endTime = span.getTraceContext().getClock().getEpochMicros();
            if (span instanceof Span) {
                long durationMicros = endTime - span.getTimestamp();
                if (cdiMethodThresholdMicros <= 0 || durationMicros >= cdiMethodThresholdMicros || t != null) {
                    span.setDiscard(false);
                }
            }
            span.deactivate().end(endTime);
        }
    }

    public CdiInstrumentation(ElasticApmTracer tracer) {
        applicationPackages = tracer.getConfig(StacktraceConfiguration.class).getApplicationPackages();
        cdiMethodThresholdMicros = tracer.getConfig(CdiConfiguration.class).getTraceMethodsDurationThreshold().getMillis() * 1000;
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(classLoaderCanLoadClass("javax.inject.Scope")
                .or(classLoaderCanLoadClass("javax.enterprise.context.NormalScope")));
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        // setting application_packages makes this matcher more performant but is not required
        // could lead to false negative matches when importing a 3rd party library whose cdi resources are exposed
        return isInAnyPackage(applicationPackages, ElementMatchers.<NamedElement>any());
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        // Any class that is annotated with an annotation that is itself annotated with @NormalScope or @Scope
        return isAnnotatedWith(
            isAnnotatedWith(
                ElementMatchers.named("javax.enterprise.context.NormalScope")
                    .or(ElementMatchers.named("javax.inject.Scope"))));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isPublic()
            .and(not(isConstructor()))
            .and(not(isStatic()))
            .and(not(isHashCode()))
            .and(not(isEquals()))
            .and(not(isToString()));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("cdi");
    }
}
