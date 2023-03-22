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
package co.elastic.apm.agent.ecs_logging.logback;

import co.elastic.apm.agent.ecs_logging.EcsLoggingInstrumentation;
import co.elastic.apm.agent.ecs_logging.EcsLoggingUtils;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.logging.logback.EcsEncoder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

public abstract class LogBackServiceInstrumentation extends EcsLoggingInstrumentation {

    @Override
    protected String getLoggingInstrumentationGroupName() {
        return "logback-ecs";
    }

    @Override
    public ElementMatcher.Junction<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.logging.logback.EcsEncoder");
    }

    /**
     * Instruments {@link EcsEncoder()} to set value for service name
     */
    public static class Name extends LogBackServiceInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return isConstructor();
        }

        public static class AdviceClass {

            private static final ElasticApmTracer tracer = GlobalTracer.requireTracerImpl();

            @Advice.OnMethodExit(inline = false)
            public static void onExit(@Advice.This EcsEncoder ecsFormatter) {
                ecsFormatter.setServiceName(EcsLoggingUtils.getServiceName(tracer));
            }
        }
    }

    /**
     * Instruments {@link EcsEncoder#setServiceName(String)} to warn potential mis-configuration
     */
    public static class NameWarn extends LogBackServiceInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("setServiceName");
        }

        public static class AdviceClass {

            private static final ElasticApmTracer tracer = GlobalTracer.requireTracerImpl();

            @Advice.OnMethodExit(inline = false)
            public static void onExit(@Advice.Argument(0) @Nullable String name) {
                EcsLoggingUtils.warnIfServiceNameMisconfigured(name, tracer);
            }
        }
    }

    /**
     * Instruments {@link EcsEncoder()} to set value for service version
     */
    public static class Version extends LogBackServiceInstrumentation {

        @Override
        public ElementMatcher.Junction<? super TypeDescription> getTypeMatcher() {
            return named("co.elastic.logging.logback.EcsEncoder")
                // setServiceVersion introduced in 1.4.0
                .and(declaresMethod(named("setServiceVersion")));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return isConstructor();
        }

        public static class AdviceClass {

            private static final ElasticApmTracer tracer = GlobalTracer.requireTracerImpl();

            @Advice.OnMethodExit(inline = false)
            public static void onExit(@Advice.This EcsEncoder ecsFormatter) {
                ecsFormatter.setServiceVersion(EcsLoggingUtils.getServiceVersion(tracer));
            }
        }
    }

    /**
     * Instruments {@link EcsEncoder#setServiceVersion(String)} to warn potential mis-configuration
     */
    public static class VersionWarn extends LogBackServiceInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("setServiceVersion");
        }

        public static class AdviceClass {

            private static final ElasticApmTracer tracer = GlobalTracer.requireTracerImpl();

            @Advice.OnMethodExit(inline = false)
            public static void onExit(@Advice.Argument(0) @Nullable String version) {
                EcsLoggingUtils.warnIfServiceVersionMisconfigured(version, tracer);
            }
        }
    }
}
