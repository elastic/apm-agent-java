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
package co.elastic.apm.agent.opentelemetry;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfigurationImpl;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.opentelemetry.tracing.OTelHelper;
import co.elastic.apm.agent.sdk.bytebuddy.AnnotationValueOffsetMappingFactory;
import co.elastic.apm.agent.sdk.bytebuddy.SimpleMethodSignatureOffsetMappingFactory;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.TraceState;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.configuration.CoreConfiguration;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.isProxy;
import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.overridesOrImplementsMethodThat;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

public class WithSpanInstrumentation extends AbstractOpenTelemetryInstrumentation {
    public static final Logger logger = LoggerFactory.getLogger(WithSpanInstrumentation.class);

    protected static final Tracer tracer = GlobalTracer.get();

    private final CoreConfiguration coreConfig;
    private final StacktraceConfigurationImpl stacktraceConfig;

    public WithSpanInstrumentation(ElasticApmTracer tracer) {
        coreConfig = tracer.getConfig(CoreConfiguration.class);
        stacktraceConfig = tracer.getConfig(StacktraceConfigurationImpl.class);
    }

    @Override
    public final ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return classLoaderCanLoadClass("io.opentelemetry.instrumentation.annotations.WithSpan");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        List<String> ret = new ArrayList<>(super.getInstrumentationGroupNames());
        ret.add("opentelemetry-annotations");
        return ret;
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
            return overridesOrImplementsMethodThat(isAnnotatedWith(named("io.opentelemetry.instrumentation.annotations.WithSpan")));
        }
        return isAnnotatedWith(named("io.opentelemetry.instrumentation.annotations.WithSpan"));
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.opentelemetry.WithSpanInstrumentation$AdviceClass";
    }

    public static class AdviceClass {
        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onMethodEnter(
            @SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature String signature,
            @AnnotationValueOffsetMappingFactory.AnnotationValueExtractor(annotationClassName = "io.opentelemetry.instrumentation.annotations.WithSpan", method = "value") String spanName,
            @AnnotationValueOffsetMappingFactory.AnnotationValueExtractor(annotationClassName = "io.opentelemetry.instrumentation.annotations.WithSpan", method = "kind") SpanKind otelKind,
            @Advice.Origin Method method,
            @Advice.AllArguments Object[] methodArguments) {

            TraceState<?> activeContext = tracer.currentContext();
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

            Span<?> span = activeContext.createSpan();
            if (span == null) {
                return null;
            }

            // process parameters that annotated with `io.opentelemetry.instrumentation.annotations.SpanAttribute` annotation
            int argsLength = methodArguments.length;
            if (argsLength > 0) {
                Annotation[][] parameterAnnotations = method.getParameterAnnotations();
                for (int i = 0; i < argsLength; i++) {
                    Annotation[] parameterAnnotation = parameterAnnotations[i];
                    int parameterAnnotationLength = parameterAnnotation.length;
                    for (int j = 0; j < parameterAnnotationLength; j++) {
                        if (parameterAnnotation[j] instanceof SpanAttribute) {
                            SpanAttribute spanAttribute = (SpanAttribute) parameterAnnotation[j];
                            String attributeName = spanAttribute.value();
                            if (!attributeName.isEmpty()) {
                                span.withOtelAttribute(attributeName, methodArguments[i]);
                            }
                            break;
                        }
                    }
                }
            }

            span.withName(spanName.isEmpty() ? signature : spanName)
                .activate();

            ((SpanImpl) span).withOtelKind(OTelHelper.map(otelKind));

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
}
