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

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.ServiceInfo;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.logging.log4j2.EcsLayout;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class Log4j2ServiceVersionInstrumentation extends AbstractLog4j2ServiceInstrumentation {

    @Override
    public ElementMatcher.Junction<? super TypeDescription> getTypeMatcher() {
        return super.getTypeMatcher().and(declaresMethod(named("setServiceVersion")));
    }

    public static class AdviceClass {

        private static final ElasticApmTracer tracer = GlobalTracer.get().require(ElasticApmTracer.class);

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onEnter(@Advice.This EcsLayout.Builder builder) {
            if (builder.getServiceVersion() == null || builder.getServiceVersion().isEmpty()) {
                ServiceInfo serviceInfo = tracer.getServiceInfoForClassLoader(Thread.currentThread().getContextClassLoader());
                String configuredServiceVersion = tracer.getConfig(CoreConfiguration.class).getServiceVersion();
                builder.setServiceVersion(serviceInfo != null ? serviceInfo.getServiceVersion() : configuredServiceVersion);
            }
        }
    }
}
