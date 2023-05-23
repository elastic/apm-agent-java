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
import co.elastic.logging.logback.EcsEncoder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToFields.ToField;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.declaresField;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments {@link EcsEncoder#start()} to set value for service name
 */
public abstract class LogBackServiceInstrumentation extends EcsLoggingInstrumentation {

    @Override
    public ElementMatcher.Junction<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.logging.logback.EcsEncoder");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("start");
    }

    public static class Name extends LogBackServiceInstrumentation {

        public static class AdviceClass {

            @Nullable
            @Advice.AssignReturned.ToFields(@ToField("serviceName"))
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static String onExit(@Advice.This Object encoder,
                                        @Advice.FieldValue("serviceName") @Nullable String serviceName) {

                return EcsLoggingUtils.getOrWarnServiceName(encoder, serviceName);
            }
        }
    }

    public static class Version extends LogBackServiceInstrumentation {

        @Override
        public ElementMatcher.Junction<? super TypeDescription> getTypeMatcher() {
            return super.getTypeMatcher()
                // serviceVersion introduced in 1.4.0
                .and(declaresField(named("serviceVersion")));
        }

        public static class AdviceClass {

            @Nullable
            @Advice.AssignReturned.ToFields(@ToField("serviceVersion"))
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static String onExit(@Advice.This Object encoder,
                                        @Advice.FieldValue("serviceVersion") @Nullable String serviceVersion) {

                return EcsLoggingUtils.getOrWarnServiceVersion(encoder, serviceVersion);
            }
        }

    }

    public static class Environment extends LogBackServiceInstrumentation {

        @Override
        public ElementMatcher.Junction<? super TypeDescription> getTypeMatcher() {
            return super.getTypeMatcher()
                // setServiceVersion introduced in 1.5.0
                .and(declaresMethod(named("setServiceEnvironment")));
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
