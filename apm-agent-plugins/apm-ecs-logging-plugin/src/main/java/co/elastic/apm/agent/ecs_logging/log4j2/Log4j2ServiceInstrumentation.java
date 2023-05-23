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
package co.elastic.apm.agent.ecs_logging.log4j2;

import co.elastic.apm.agent.ecs_logging.EcsLoggingInstrumentation;
import co.elastic.apm.agent.ecs_logging.EcsLoggingUtils;
import co.elastic.logging.log4j2.EcsLayout;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToFields.ToField;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.declaresField;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments {@link EcsLayout.Builder#build()}
 */
public abstract class Log4j2ServiceInstrumentation extends EcsLoggingInstrumentation {

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("build");
    }

    public static class Name extends Log4j2ServiceInstrumentation {

        @Override
        public ElementMatcher.Junction<? super TypeDescription> getTypeMatcher() {
            return named("co.elastic.logging.log4j2.EcsLayout$Builder");
        }

        public static class AdviceClass {

            @Nullable
            @Advice.AssignReturned.ToFields(@ToField("serviceName"))
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static String onEnter(@Advice.This Object builder,
                                         @Advice.FieldValue("serviceName") @Nullable String serviceName) {

                return EcsLoggingUtils.getOrWarnServiceName(builder, serviceName);
            }
        }
    }

    public static class Version extends Log4j2ServiceInstrumentation {

        @Override
        public ElementMatcher.Junction<? super TypeDescription> getTypeMatcher() {
            return named("co.elastic.logging.log4j2.EcsLayout$Builder")
                // serviceVersion introduced in 1.4.0
                .and(declaresField(named("serviceVersion")));
        }

        public static class AdviceClass {

            @Nullable
            @Advice.AssignReturned.ToFields(@ToField("serviceVersion"))
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static String onEnter(@Advice.This Object builder,
                                         @Advice.FieldValue("serviceVersion") @Nullable String serviceVersion) {

                return EcsLoggingUtils.getOrWarnServiceVersion(builder, serviceVersion);
            }
        }
    }

    public static class Environment extends Log4j2ServiceInstrumentation {

        @Override
        public ElementMatcher.Junction<? super TypeDescription> getTypeMatcher() {
            return named("co.elastic.logging.log4j2.EcsLayout$Builder")
                // serviceEnvironment introduced in 1.5.0
                .and(declaresField(named("serviceEnvironment")));
        }

        public static class AdviceClass {

            @Nullable
            @Advice.AssignReturned.ToFields(@ToField(value = "serviceEnvironment"))
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static String onEnter(@Advice.This Object formatter,
                                         @Advice.FieldValue("serviceEnvironment") @Nullable String serviceEnvironment) {

                return EcsLoggingUtils.getOrWarnServiceEnvironment(formatter, serviceEnvironment);
            }
        }

    }

}
