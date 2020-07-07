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
package co.elastic.apm.agent.log.shader.log4j2;

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
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;
import org.apache.logging.log4j.core.appender.FileAppender;

import javax.annotation.Nullable;
import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class Log4j2LogShadingInstrumentation extends AbstractLogShadingInstrumentation {

    // Logback class referencing is allowed thanks to type erasure
    @VisibleForAdvice
    @Nullable
    public static HelperClassManager<AbstractLogShadingHelper<AbstractOutputStreamAppender<?>>> helperClassManager;

    public Log4j2LogShadingInstrumentation(ElasticApmTracer tracer) {
        synchronized (Log4j2LogShadingInstrumentation.class) {
            if (helperClassManager == null) {
                helperClassManager = HelperClassManager.ForAnyClassLoader.of(tracer,
                    "co.elastic.apm.agent.log.shader.log4j2.helper.Log4j2LogShadingHelper",
                    "co.elastic.logging.log4j2.EcsLayout",
                    "co.elastic.logging.log4j2.EcsLayout$Builder",
                    "co.elastic.logging.log4j2.EcsLayout$1",
                    "co.elastic.logging.log4j2.ObjectMapperFactory",
                    "co.elastic.logging.log4j2.ObjectMessageJacksonSerializer",
                    "co.elastic.logging.log4j2.ObjectMessageJacksonSerializer$Available",
                    "co.elastic.logging.log4j2.ObjectMessageJacksonSerializer$Resolver"
                );
            }
        }
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        Collection<String> ret = super.getInstrumentationGroupNames();
        ret.add("log4j2");
        return ret;
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(classLoaderCanLoadClass("org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender");
    }

    public static class ShadingInstrumentation extends Log4j2LogShadingInstrumentation {

        public ShadingInstrumentation(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("append").and(takesArgument(0, named("org.apache.logging.log4j.core.LogEvent")));
        }

        @Override
        public Class<?> getAdviceClass() {
            return Log4j2AppenderAdvice.class;
        }

        public static class Log4j2AppenderAdvice {

            @SuppressWarnings({"unused", "ConstantConditions"})
            @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
            public static void shadeLoggingEvent(@Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) final LogEvent eventObject,
                                                 @Advice.This(typing = Assigner.Typing.DYNAMIC) AbstractOutputStreamAppender<?> thisAppender) {

                AbstractLogShadingHelper<AbstractOutputStreamAppender<?>> helper =
                    helperClassManager.getForClassLoaderOfClass(AbstractOutputStreamAppender.class);
                AbstractOutputStreamAppender<?> shadeAppender = helper.getOrCreateShadeAppenderFor(thisAppender);
                if (shadeAppender != null) {
                    shadeAppender.append(eventObject);
                }
            }
        }
    }

    public static class StopAppenderInstrumentation extends Log4j2LogShadingInstrumentation {

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
            public static void shadeLoggingEvent(@Advice.This(typing = Assigner.Typing.DYNAMIC) AbstractOutputStreamAppender<?> thisAppender) {
                if (!(thisAppender instanceof FileAppender)) {
                    return;
                }
                AbstractLogShadingHelper<AbstractOutputStreamAppender<?>> helper =
                    helperClassManager.getForClassLoaderOfClass(AbstractOutputStreamAppender.class);
                helper.stopShading(thisAppender);
            }
        }
    }
}
