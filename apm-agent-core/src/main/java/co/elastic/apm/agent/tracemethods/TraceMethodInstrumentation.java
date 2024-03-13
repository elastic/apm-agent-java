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
package co.elastic.apm.agent.tracemethods;

import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.sdk.bytebuddy.SimpleMethodSignatureOffsetMappingFactory;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpanImpl;
import co.elastic.apm.agent.impl.transaction.TraceStateImpl;
import co.elastic.apm.agent.matcher.MethodMatcher;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.sdk.internal.util.PrivilegedActionUtils;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Tracer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import javax.annotation.Nullable;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.isProxy;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.matches;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isNative;
import static net.bytebuddy.matcher.ElementMatchers.isSynthetic;
import static net.bytebuddy.matcher.ElementMatchers.isTypeInitializer;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class TraceMethodInstrumentation extends ElasticApmInstrumentation {

    private final MethodMatcher methodMatcher;
    private final CoreConfigurationImpl config;

    public TraceMethodInstrumentation(Tracer tracer, MethodMatcher methodMatcher) {
        this.methodMatcher = methodMatcher;
        config = tracer.getConfig(CoreConfigurationImpl.class);
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return matches(methodMatcher.getClassMatcher())
            .and(not(isProxy()))
            .and(methodMatcher.getAnnotationMatcher())
            .and(declaresMethod(matches(methodMatcher.getMethodMatcher())));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        ElementMatcher.Junction<? super MethodDescription> matcher = matches(methodMatcher.getMethodMatcher());

        final List<WildcardMatcher> methodsExcludedFromInstrumentation = config.getMethodsExcludedFromInstrumentation();
        if (!methodsExcludedFromInstrumentation.isEmpty()) {
            matcher = matcher.and(not(new ElementMatcher<MethodDescription>() {
                @Override
                public boolean matches(MethodDescription target) {
                    return WildcardMatcher.anyMatch(methodsExcludedFromInstrumentation, target.getActualName()) != null;
                }
            }));
        }

        if (methodMatcher.getModifier() != null) {
            switch (methodMatcher.getModifier()) {
                case Modifier.PUBLIC:
                    matcher = matcher.and(ElementMatchers.isPublic());
                    break;
                case Modifier.PROTECTED:
                    matcher = matcher.and(ElementMatchers.isProtected());
                    break;
                case Modifier.PRIVATE:
                    matcher = matcher.and(ElementMatchers.isPrivate());
                    break;
            }
        }
        if (methodMatcher.getArgumentMatchers() != null) {
            matcher = matcher.and(takesArguments(methodMatcher.getArgumentMatchers().size()));
            List<WildcardMatcher> argumentMatchers = methodMatcher.getArgumentMatchers();
            for (int i = 0; i < argumentMatchers.size(); i++) {
                matcher = matcher.and(takesArgument(i, matches(argumentMatchers.get(i))));
            }
        }
        // Byte Buddy can't catch exceptions (onThrowable = Throwable.class) during a constructor call:
        // java.lang.IllegalStateException: Cannot catch exception during constructor call
        return matcher.and(not(isConstructor()))
            .and(not(isAbstract()))
            .and(not(isNative()))
            .and(not(isSynthetic()))
            .and(not(isTypeInitializer()));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("method-matching");
    }

    @Override
    public String getAdviceClassName() {
        return getClass().getName() + "$TraceMethodAdvice";
    }

    public static class TraceMethodAdvice {

        public static final Logger logger = LoggerFactory.getLogger(TraceMethodAdvice.class);

        private static final ElasticApmTracer tracer = GlobalTracer.get().require(ElasticApmTracer.class);
        private static final long traceMethodThresholdMicros;

        static {
            CoreConfigurationImpl config = tracer.getConfig(CoreConfigurationImpl.class);
            traceMethodThresholdMicros = config.getTraceMethodsDurationThreshold().getMicros();
        }

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onMethodEnter(@Advice.Origin Class<?> clazz,
                                           @SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature String signature) {
            AbstractSpanImpl<?> span = null;
            TraceStateImpl<?> activeContext = tracer.currentContext();
            final AbstractSpanImpl<?> parentSpan = tracer.getActive();
            if (parentSpan == null) {
                span = tracer.startRootTransaction(PrivilegedActionUtils.getClassLoader(clazz));
                if (span != null) {
                    span.withName(signature).activate();
                }
            } else if (parentSpan.isSampled()) {
                if (parentSpan.shouldSkipChildSpanCreation()) {
                    // span limit reached means span will not be reported, thus we can optimize and skip creating one
                    logger.debug("Not creating span for {} because span limit is reached.", signature);
                    return null;
                }
                span = activeContext.createSpan()
                    .withName(signature)
                    .activate();
            }
            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onMethodExit(@Advice.Enter @Nullable Object spanObj,
                                        @Advice.Thrown @Nullable Throwable t) {
            AbstractSpanImpl<?> span = (AbstractSpanImpl<?>) spanObj;
            if (span != null) {
                span.captureException(t);
                final long endTime = span.getTraceContext().getClock().getEpochMicros();
                if (span instanceof SpanImpl) {
                    long durationMicros = endTime - span.getTimestamp();
                    if (traceMethodThresholdMicros > 0 && durationMicros < traceMethodThresholdMicros && t == null) {
                        span.requestDiscarding();
                    }
                }
                span.deactivate().end(endTime);
            }
        }
    }

}
