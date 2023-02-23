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

import co.elastic.apm.agent.sdk.TracerAwareInstrumentation;
import co.elastic.apm.agent.sdk.utils.CustomElementMatchers;
import co.elastic.apm.tracer.api.service.ServiceAwareTracer;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

public abstract class AbstractLog4j2ServiceInstrumentation extends TracerAwareInstrumentation {

    public AbstractLog4j2ServiceInstrumentation(ServiceAwareTracer ignored) {
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(CustomElementMatchers.isAgentClassLoader());
    }

    @Override
    public ElementMatcher.Junction<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.logging.log4j2.EcsLayout$Builder");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("build");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("logging", "log4j2-ecs");
    }
}
