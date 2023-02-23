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

import co.elastic.apm.agent.sdk.TracerAwareInstrumentation;
import co.elastic.apm.agent.bci.bytebuddy.AnnotationValueOffsetMappingFactory;
import co.elastic.apm.agent.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory;
import co.elastic.apm.agent.sdk.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.util.PrivilegedActionUtils;
import co.elastic.apm.tracer.api.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.*;
import static co.elastic.apm.tracer.api.AbstractSpan.PRIO_METHOD_SIGNATURE;
import static co.elastic.apm.tracer.api.AbstractSpan.PRIO_USER_SUPPLIED;
import static co.elastic.apm.agent.pluginapi.ElasticApmApiInstrumentation.PUBLIC_API_INSTRUMENTATION_GROUP;
import static co.elastic.apm.agent.pluginapi.Utils.FRAMEWORK_NAME;
import static net.bytebuddy.matcher.ElementMatchers.*;

public class TracedInstrumentation extends TracerAwareInstrumentation {

    private final co.elastic.apm.agent.sdk.configuration.CoreConfiguration coreConfig;
    private final StacktraceConfiguration stacktraceConfig;

    public TracedInstrumentation(co.elastic.apm.tracer.api.Tracer tracer) {
        coreConfig = tracer.getConfig(CoreConfiguration.class);
        stacktraceConfig = tracer.getConfig(StacktraceConfiguration.class);
    }

    public static class AdviceClass {
        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onMethodEnter(
            @Advice.Origin Class<?> clazz,
            @SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature String signature,
            @AnnotationValueOffsetMappingFactory.AnnotationValueExtractor(annotationClassName = "co.elastic.apm.api.Traced", method = "value") String spanName,
            @AnnotationValueOffsetMappingFactory.AnnotationValueExtractor(annotationClassName = "co.elastic.apm.api.Traced", method = "type") String type,
            @AnnotationValueOffsetMappingFactory.AnnotationValueExtractor(annotationClassName = "co.elastic.apm.api.Traced", method = "subtype") @Nullable String subtype,
            @AnnotationValueOffsetMappingFactory.AnnotationValueExtractor(annotationClassName = "co.elastic.apm.api.Traced", method = "action") @Nullable String action,
            @AnnotationValueOffsetMappingFactory.AnnotationValueExtractor(
                annotationClassName = "co.elastic.apm.api.Traced",
                method = "discardable",
                defaultValueProvider = AnnotationValueOffsetMappingFactory.TrueDefaultValueProvider.class
            ) boolean discardable) {

            final co.elastic.apm.tracer.api.AbstractSpan<?> parent = tracer.getActive();
            if (parent != null) {
                co.elastic.apm.tracer.api.Span<?> span = parent.createSpan()
                    .withType(type.isEmpty() ? "app" : type)
                    .withSubtype(subtype)
                    .withAction(action)
                    .withName(spanName.isEmpty() ? signature : spanName);
                if (!discardable) {
                    span.setNonDiscardable();
                }
                return span.activate();
            }

            co.elastic.apm.tracer.api.Transaction<?> transaction = tracer.startRootTransaction(PrivilegedActionUtils.getClassLoader(clazz));
            if (transaction == null) {
                return null;
            }

            transaction.setFrameworkName(FRAMEWORK_NAME);
            String name;
            int namePriority;
            if (spanName.isEmpty()) {
                name = signature;
                namePriority = PRIO_METHOD_SIGNATURE;
            } else {
                name = spanName;
                namePriority = PRIO_USER_SUPPLIED;
            }
            return transaction.withName(name, namePriority)
                .withType(type.isEmpty() ? Transaction.TYPE_REQUEST : type)
                .activate();
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onMethodExit(@Advice.Enter @Nullable Object abstractSpan,
                                        @Advice.Thrown @Nullable Throwable t) {
            if (abstractSpan instanceof co.elastic.apm.tracer.api.AbstractSpan<?>) {
                ((co.elastic.apm.tracer.api.AbstractSpan<?>) abstractSpan)
                    .captureException(t)
                    .withOutcome(t != null ? co.elastic.apm.tracer.api.DefaultOutcome.FAILURE : co.elastic.apm.tracer.api.DefaultOutcome.SUCCESS)
                    .deactivate()
                    .end();
            }
        }
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return classLoaderCanLoadClass("co.elastic.apm.api.Traced");
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
            return overridesOrImplementsMethodThat(isAnnotatedWith(named("co.elastic.apm.api.Traced")));
        }
        return isAnnotatedWith(named("co.elastic.apm.api.Traced"));
    }

    @Override
    public boolean includeWhenInstrumentationIsDisabled() {
        return false;
    }

    @Override
    public final Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList(PUBLIC_API_INSTRUMENTATION_GROUP, "annotations", "annotations-traced");
    }
}
