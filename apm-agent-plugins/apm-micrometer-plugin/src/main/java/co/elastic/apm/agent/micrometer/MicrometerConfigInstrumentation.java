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
package co.elastic.apm.agent.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleConfig;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * SimpleMeterRegistry is constructed with a SimpleConfig, but the config is not accessible via
 * a config() method because the superclass declares a config() which returns a different config
 * private to the superclass, and the SimpleMeterRegistry doesn't override that nor provide a different
 * method to access the SimpleConfig. So this instrumentation is just to access that config.
 */
public class MicrometerConfigInstrumentation extends AbstractMicrometerInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.micrometer.core.instrument.simple.SimpleMeterRegistry");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isConstructor().and(takesArgument(0, named("io.micrometer.core.instrument.simple.SimpleConfig")));
    }

    public static class AdviceClass {
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void onExit(@Advice.This MeterRegistry meterRegistry, @Advice.Argument(0) SimpleConfig config) {
            reporter.addConfig(meterRegistry, config);

        }
    }

}
