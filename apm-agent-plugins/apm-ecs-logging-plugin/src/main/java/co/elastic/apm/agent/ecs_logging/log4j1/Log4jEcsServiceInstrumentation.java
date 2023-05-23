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
package co.elastic.apm.agent.ecs_logging.log4j1;

import co.elastic.apm.agent.ecs_logging.EcsLoggingInstrumentation;
import co.elastic.apm.agent.ecs_logging.EcsLoggingUtils;
import co.elastic.logging.log4j.EcsLayout;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToFields.ToField;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.log4j.spi.LoggingEvent;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments {@link EcsLayout#format(LoggingEvent)}
 */
public abstract class Log4jEcsServiceInstrumentation extends EcsLoggingInstrumentation {

    @Override
    public ElementMatcher.Junction<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.logging.log4j.EcsLayout");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("format");
    }

    public static class Name extends Log4jEcsServiceInstrumentation {

        public static class AdviceClass {

            @Nullable
            @Advice.AssignReturned.ToFields(@ToField("serviceName"))
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static String onEnter(@Advice.This Object layout,
                                         @Advice.FieldValue("serviceName") @Nullable String serviceName) {

                return EcsLoggingUtils.getOrWarnServiceName(layout, serviceName);
            }
        }

    }

    public static class Version extends Log4jEcsServiceInstrumentation {

        @Override
        public ElementMatcher.Junction<? super TypeDescription> getTypeMatcher() {
            return super.getTypeMatcher()
                // setServiceVersion introduced in 1.4.0
                .and(declaresMethod(named("setServiceVersion")));
        }

        public static class AdviceClass {

            @Nullable
            @Advice.AssignReturned.ToFields(@ToField("serviceVersion"))
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static String onEnter(@Advice.This Object layout,
                                         @Advice.FieldValue("serviceVersion") @Nullable String serviceVersion) {

                return EcsLoggingUtils.getOrWarnServiceVersion(layout, serviceVersion);
            }
        }

    }

    public static class Environment extends Log4jEcsServiceInstrumentation {

        @Override
        public ElementMatcher.Junction<? super TypeDescription> getTypeMatcher() {
            return super.getTypeMatcher()
                // setServiceEnvironment introduced in 1.5.0
                .and(declaresMethod(named("setServiceEnvironment")));
        }

        public static class AdviceClass {

            @Nullable
            @Advice.AssignReturned.ToFields(@ToField("serviceEnvironment"))
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static String onEnter(@Advice.This Object layout,
                                         @Advice.FieldValue("serviceEnvironment") @Nullable String serviceEnvironment) {

                return EcsLoggingUtils.getOrWarnServiceEnvironment(layout, serviceEnvironment);
            }
        }

    }
}
