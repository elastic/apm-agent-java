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
package co.elastic.apm.agent.pluginapi;

import co.elastic.apm.agent.sdk.bytebuddy.AnnotationValueOffsetMappingFactory;
import co.elastic.apm.agent.sdk.bytebuddy.SimpleMethodSignatureOffsetMappingFactory;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.ElasticContext;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.configuration.CoreConfiguration;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.isProxy;
import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.overridesOrImplementsMethodThat;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

public class CaptureSpanInstrumentation extends ElasticApmInstrumentation {

    public static final Logger logger = LoggerFactory.getLogger(CaptureSpanInstrumentation.class);

    protected static final Tracer tracer = GlobalTracer.get();

    private final CoreConfiguration coreConfig;
    private final StacktraceConfiguration stacktraceConfig;

    public CaptureSpanInstrumentation(ElasticApmTracer tracer) {
        coreConfig = tracer.getConfig(CoreConfiguration.class);
        stacktraceConfig = tracer.getConfig(StacktraceConfiguration.class);
    }

    public static class AdviceClass {
        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onMethodEnter(
            @SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature String signature,
            @AnnotationValueOffsetMappingFactory.AnnotationValueExtractor(annotationClassName = "co.elastic.apm.api.CaptureSpan", method = "value") String spanName,
            @AnnotationValueOffsetMappingFactory.AnnotationValueExtractor(annotationClassName = "co.elastic.apm.api.CaptureSpan", method = "type") String type,
            @AnnotationValueOffsetMappingFactory.AnnotationValueExtractor(annotationClassName = "co.elastic.apm.api.CaptureSpan", method = "subtype") @Nullable String subtype,
            @AnnotationValueOffsetMappingFactory.AnnotationValueExtractor(annotationClassName = "co.elastic.apm.api.CaptureSpan", method = "action") @Nullable String action,
            @AnnotationValueOffsetMappingFactory.AnnotationValueExtractor(
                annotationClassName = "co.elastic.apm.api.CaptureSpan",
                method = "asExit",
                defaultValueProvider = AnnotationValueOffsetMappingFactory.FalseDefaultValueProvider.class
            ) boolean asExit,
            @AnnotationValueOffsetMappingFactory.AnnotationValueExtractor(
                annotationClassName = "co.elastic.apm.api.CaptureSpan",
                method = "discardable",
                defaultValueProvider = AnnotationValueOffsetMappingFactory.TrueDefaultValueProvider.class
            ) boolean discardable
        ) {
            ElasticContext<?> activeContext = tracer.currentContext();
            final AbstractSpan<?> parentSpan = activeContext.getSpan();
            if (parentSpan == null) {
                logger.debug("Not creating span for {} because there is no currently active span.", signature);
                return null;
            }
            if (activeContext.shouldSkipChildSpanCreation()) {
                // span limit reached means span will not be reported, thus we can optimize and skip creating one
                logger.debug("Not creating span for {} because span limit is reached.", signature);
                return null;
            }

            Span<?> span = asExit ? activeContext.createExitSpan() : activeContext.createSpan();
            if (span == null) {
                return null;
            }

            span.withName(spanName.isEmpty() ? signature : spanName)
                .activate();

            // using deprecated API to keep compatibility with existing behavior
            ((co.elastic.apm.agent.impl.transaction.Span) span).setType(type, subtype, action);

            if (!discardable) {
                span.setNonDiscardable();
            }
            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onMethodExit(@Advice.Enter @Nullable Object span,
                                        @Advice.Thrown @Nullable Throwable t) {
            if (span instanceof Span<?>) {
                ((Span<?>) span)
                    .captureException(t)
                    .withOutcome(t != null ? Outcome.FAILURE : Outcome.SUCCESS)
                    .deactivate()
                    .end();
            }
        }
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return classLoaderCanLoadClass("co.elastic.apm.api.CaptureSpan");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return isInAnyPackage(stacktraceConfig.getApplicationPackages(), ElementMatchers.<NamedElement>none())
            .and(not(isProxy()))
            .and(declaresMethod(getMethodMatcher()));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        if (coreConfig.isEnablePublicApiAnnotationInheritance()) {
            return overridesOrImplementsMethodThat(isAnnotatedWith(named("co.elastic.apm.api.CaptureSpan")));
        }
        return isAnnotatedWith(named("co.elastic.apm.api.CaptureSpan"));
    }

    @Override
    public boolean includeWhenInstrumentationIsDisabled() {
        return false;
    }

    @Override
    public final Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList(ElasticApmApiInstrumentation.PUBLIC_API_INSTRUMENTATION_GROUP, "annotations", "annotations-capture-span");
    }
}
