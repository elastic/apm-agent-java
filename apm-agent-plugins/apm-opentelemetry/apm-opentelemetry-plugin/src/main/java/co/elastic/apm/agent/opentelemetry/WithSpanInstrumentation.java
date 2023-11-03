package co.elastic.apm.agent.opentelemetry;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.opentelemetry.tracing.OTelHelper;
import co.elastic.apm.agent.sdk.bytebuddy.AnnotationValueOffsetMappingFactory;
import co.elastic.apm.agent.sdk.bytebuddy.SimpleMethodSignatureOffsetMappingFactory;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.ElasticContext;
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
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

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
    private final StacktraceConfiguration stacktraceConfig;

    public WithSpanInstrumentation(ElasticApmTracer tracer) {
        coreConfig = tracer.getConfig(CoreConfiguration.class);
        stacktraceConfig = tracer.getConfig(StacktraceConfiguration.class);
    }

    @Override
    public final ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return classLoaderCanLoadClass("io.opentelemetry.instrumentation.annotations.WithSpan");
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
            @Advice.AllArguments(typing = Assigner.Typing.DYNAMIC) Object[] methodArguments) {

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

            ((co.elastic.apm.agent.impl.transaction.Span) span).withOtelKind(OTelHelper.map(otelKind));

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
