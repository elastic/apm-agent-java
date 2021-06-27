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
package co.elastic.apm.agent.reactor;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments Mono/Flux to automatically register context-propagation hook
 * <ul>
 *     <li>{@link reactor.core.publisher.Mono#onAssembly}</li>
 *     <li>{@link reactor.core.publisher.Flux#onAssembly}</li>
 * </ul>
 */
@SuppressWarnings("JavadocReference")
public class ReactorInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("reactor.core.publisher.Mono")
            .or(named("reactor.core.publisher.Flux"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isStatic().and(named("onAssembly"));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("reactor", "experimental");
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.reactor.ReactorInstrumentation$RegisterHookAdvice";
    }

    public static class RegisterHookAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onEnter() {
            TracedSubscriber.registerHooks(tracer);
        }

    }


}
