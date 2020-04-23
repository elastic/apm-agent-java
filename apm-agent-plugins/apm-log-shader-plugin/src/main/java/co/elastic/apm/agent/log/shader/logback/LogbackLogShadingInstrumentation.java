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
package co.elastic.apm.agent.log.shader.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.OutputStreamAppender;
import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.log.shader.AbstractLogShadingHelper;
import co.elastic.apm.agent.log.shader.AbstractLogShadingInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.takesGenericArgument;

public abstract class LogbackLogShadingInstrumentation extends AbstractLogShadingInstrumentation {

    // Logback class referencing is allowed thanks to type erasure
    @VisibleForAdvice
    @Nullable
    public static HelperClassManager<AbstractLogShadingHelper<FileAppender<ILoggingEvent>>> helperClassManager;

    public LogbackLogShadingInstrumentation(ElasticApmTracer tracer) {
        synchronized (LogbackLogShadingInstrumentation.class) {
            if (helperClassManager == null) {
                helperClassManager = HelperClassManager.ForAnyClassLoader.of(tracer,
                    "co.elastic.apm.agent.log.shader.logback.helper.LogbackLogShadingHelper",
                    "co.elastic.logging.logback.EcsEncoder"
                );
            }
        }
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        Collection<String> ret = super.getInstrumentationGroupNames();
        ret.add("logback");
        return ret;
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader()).and(classLoaderCanLoadClass("ch.qos.logback.core.OutputStreamAppender"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("ch.qos.logback.core.OutputStreamAppender");
    }

    public static class ShadingInstrumentation extends LogbackLogShadingInstrumentation {

        public ShadingInstrumentation(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("append").and(takesGenericArgument(0, TypeDescription.Generic.Builder.typeVariable("E").build()));
        }

        @Override
        public Class<?> getAdviceClass() {
            return LogbackAppenderAdvice.class;
        }

        public static class LogbackAppenderAdvice {
            @SuppressWarnings({"unused", "ConstantConditions"})
            @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
            public static void shadeLoggingEvent(@Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) final Object eventObject,
                                                 @Advice.This(typing = Assigner.Typing.DYNAMIC) OutputStreamAppender<ILoggingEvent> thisAppender) {
                if (!(thisAppender instanceof FileAppender) || !(eventObject instanceof ILoggingEvent)) {
                    return;
                }
                AbstractLogShadingHelper<FileAppender<ILoggingEvent>> helper = helperClassManager.getForClassLoaderOfClass(OutputStreamAppender.class);

                FileAppender<ILoggingEvent> shadeAppender = helper.getOrCreateShadeAppenderFor((FileAppender<ILoggingEvent>) thisAppender);

                if (shadeAppender != null) {
                    // We do not invoke the exact same method we instrument, but a public API that calls it
                    shadeAppender.doAppend((ILoggingEvent) eventObject);
                }
            }
        }
    }

    public static class StopAppenderInstrumentation extends LogbackLogShadingInstrumentation {

        public StopAppenderInstrumentation(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("stop").and(takesArguments(0));
        }

        @Override
        public Class<?> getAdviceClass() {
            return StopAppenderAdvice.class;
        }

        public static class StopAppenderAdvice {
            @SuppressWarnings({"unused", "ConstantConditions"})
            @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
            public static void shadeLoggingEvent(@Advice.This(typing = Assigner.Typing.DYNAMIC) OutputStreamAppender<ILoggingEvent> thisAppender) {
                if (!(thisAppender instanceof FileAppender)) {
                    return;
                }
                AbstractLogShadingHelper<FileAppender<ILoggingEvent>> helper = helperClassManager.getForClassLoaderOfClass(OutputStreamAppender.class);
                helper.stopShading((FileAppender<ILoggingEvent>) thisAppender);
            }
        }
    }
}
