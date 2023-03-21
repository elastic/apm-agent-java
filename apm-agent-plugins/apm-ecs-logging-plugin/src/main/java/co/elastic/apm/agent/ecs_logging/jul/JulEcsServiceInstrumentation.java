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
package co.elastic.apm.agent.ecs_logging.jul;

import co.elastic.apm.agent.ecs_logging.EcsLoggingUtils;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments {@link co.elastic.logging.jul.EcsFormatter#getProperty} to provide default values
 */
@SuppressWarnings("JavadocReference")
public class JulEcsServiceInstrumentation extends JulEcsFormatterInstrumentation {

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("getProperty");
    }

    public static class AdviceClass {

        private static final ElasticApmTracer tracer = GlobalTracer.requireTracerImpl();

        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static String onExit(@Advice.Argument(0) String key,
                                    @Advice.Return @Nullable String value) {

            if (value == null) {
                if ("co.elastic.logging.jul.EcsFormatter.serviceName".equals(key)) {
                    value = EcsLoggingUtils.getServiceName(tracer);
                } else if ("co.elastic.logging.jul.EcsFormatter.serviceVersion".equals(key)) {
                    value = EcsLoggingUtils.getServiceVersion(tracer);
                }
            }
            return value;
        }


    }
}
