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

import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.sdk.state.GlobalState;
import co.elastic.logging.jul.EcsFormatter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Test-only instrumentation that allows to bypass JUL static init and configuration
 */
public class LogManagerTestInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameStartsWith("java.util.logging.");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("java.util.logging.LogManager"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("getProperty");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.emptyList();
    }

    public static class AdviceClass {

        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static String onExit(@Advice.Argument(0) String key, @Advice.Return String value) {
            Properties props = JulProperties.overridenProperties;
            if (props != null) {
                return props.getProperty(key);
            }
            return value;
        }
    }

    @GlobalState
    public static class JulProperties {

        @Nullable
        public static Properties overridenProperties = null;

        public static void override(Map<String, String> map) {
            overridenProperties = new Properties();
            overridenProperties.putAll(map);
        }

        public static void restore() {
            overridenProperties = null;
        }
    }

}
