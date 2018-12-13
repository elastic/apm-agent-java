/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.agent.plugin.api;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.bci.bytebuddy.AnnotationValueOffsetMappingFactory;
import co.elastic.apm.agent.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static co.elastic.apm.agent.plugin.api.ElasticApmApiInstrumentation.PUBLIC_API_INSTRUMENTATION_GROUP;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class CaptureSpanInstrumentation extends ElasticApmInstrumentation {

    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(CaptureSpanInstrumentation.class);

    private StacktraceConfiguration config;

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onMethodEnter(@SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature String signature,
                                     @AnnotationValueOffsetMappingFactory.AnnotationValueExtractor(annotationClassName = "co.elastic.apm.api.CaptureSpan", method = "value") String spanName,
                                     @AnnotationValueOffsetMappingFactory.AnnotationValueExtractor(annotationClassName = "co.elastic.apm.api.CaptureSpan", method = "type") String type,
                                     @Advice.Local("span") Span span) {
        if (tracer != null) {
            final AbstractSpan<?> parent = tracer.activeSpan();
            if (parent != null) {
                span = parent.createSpan()
                    .withName(spanName.isEmpty() ? signature : spanName)
                    .withType(type)
                    .activate();
            } else {
                logger.debug("Not creating span for {} because there is no currently active span.", signature);
            }
        }

    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onMethodExit(@Nullable @Advice.Local("span") Span span,
                                    @Advice.Thrown Throwable t) {
        if (span != null) {
            span.captureException(t)
                .deactivate()
                .end();
        }
    }

    @Override
    public void init(ElasticApmTracer tracer) {
        config = tracer.getConfig(StacktraceConfiguration.class);
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return classLoaderCanLoadClass("co.elastic.apm.api.CaptureSpan");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return isInAnyPackage(config.getApplicationPackages(), ElementMatchers.<NamedElement>none())
            .and(declaresMethod(getMethodMatcher()));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isAnnotatedWith(named("co.elastic.apm.api.CaptureSpan"));
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
