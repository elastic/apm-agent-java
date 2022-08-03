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

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.logging.AgentMDC;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.named;

@SuppressWarnings("JavadocReference")
public abstract class JulMdcInstrumentation extends TracerAwareInstrumentation {

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("logging", "jul-ecs");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.logging.jul.JulMdc");
    }

    /**
     * Instruments {@link co.elastic.logging.jul.JulMdc#put(String, String)} to delegate to {@link AgentMDC#put(String, String)}.
     */
    public static class Put extends JulMdcInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("put");
        }

        public static class AdviceClass {

            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false, skipOn = Advice.OnNonDefaultValue.class)
            public static boolean onEnter(@Advice.Argument(0) @Nullable String key,
                                          @Advice.Argument(1) @Nullable String value) {
                AgentMDC.put(key, value);
                return true;
            }

        }
    }

    /**
     * Instruments {@link co.elastic.logging.jul.JulMdc#remove(String)} ()} to delegate to {@link AgentMDC#remove(String)}.
     */
    public static class Remove extends JulMdcInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("remove");
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false, skipOn = Advice.OnNonDefaultValue.class)
            public static boolean onEnter(@Advice.Argument(0) @Nullable String key) {
                AgentMDC.remove(key);
                return true;
            }
        }
    }

    /**
     * Instruments {@link co.elastic.logging.jul.JulMdc#getEntries()} to delegate to {@link AgentMDC#getEntries()}.
     */
    public static class GetEntries extends JulMdcInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("getEntries");
        }

        public static class AdviceClass {

            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false, skipOn = Advice.OnNonDefaultValue.class)
            public static boolean onEnter() {
                return true;
            }

            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
            public static Map<String, String> onExit() {
                return AgentMDC.getEntries();
            }

        }
    }

}
