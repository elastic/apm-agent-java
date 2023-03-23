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
package co.elastic.apm.agent.ecs_logging;

import co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.loginstr.AbstractLogIntegrationInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.not;

public abstract class EcsLoggingInstrumentation extends AbstractLogIntegrationInstrumentation {

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        // ECS formatter that is loaded within the agent should not be instrumented
        return not(CustomElementMatchers.isInternalPluginClassLoader());
    }

    @SuppressWarnings("unused")
    public static class VersionWarnAdvice {

        private static final ElasticApmTracer tracer = GlobalTracer.requireTracerImpl();

        @Advice.OnMethodExit(inline = false)
        public static void onExit(@Advice.Argument(0) @Nullable String version) {
            EcsLoggingUtils.warnIfServiceVersionMisconfigured(version, tracer);
        }
    }

    @SuppressWarnings("unused")
    public static class NameWarnAdvice {

        private static final ElasticApmTracer tracer = GlobalTracer.requireTracerImpl();

        @Advice.OnMethodExit(inline = false)
        public static void onExit(@Advice.Argument(0) @Nullable String name) {
            EcsLoggingUtils.warnIfServiceNameMisconfigured(name, tracer);
        }
    }
}
