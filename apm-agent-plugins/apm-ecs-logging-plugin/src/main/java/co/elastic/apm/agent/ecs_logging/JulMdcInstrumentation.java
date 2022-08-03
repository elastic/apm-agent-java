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
import co.elastic.logging.jul.JulMdc;
import co.elastic.apm.agent.logging.JulMdcAccessor;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments access to {@link JulMdc} to make the JulMdc loaded within the application classloader registered to receive
 * updates from the agent.
 */
@SuppressWarnings("JavadocReference")
public class JulMdcInstrumentation extends TracerAwareInstrumentation {

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("logging", "jul-ecs");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.logging.jul.JulMdc");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        // init on first read or write access
        return named("getEntries").or(named("put"));
    }

    public static class AdviceClass {

        private static boolean isRegistered;

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onEnter(@Advice.Origin Class<?> mdcClass) {
            if (!isRegistered) {
                // will register the JulMdc that is packaged with the application
                JulMdcAccessor.register(mdcClass);
                isRegistered = true;
            }
        }
    }

}
