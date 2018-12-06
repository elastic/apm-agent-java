/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.bci;

import co.elastic.apm.MockTracer;
import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.ElasticApmTracerBuilder;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.commons.math.util.MathUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class InstrumentationTest {

    @AfterEach
    void afterAll() {
        ElasticApmAgent.reset();
    }

    @Test
    void testIntercept() {
        init(SpyConfiguration.createSpyConfig());
        assertThat(interceptMe()).isEqualTo("intercepted");
    }

    @Test
    void testDisabled() {
        final ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        when(config.getConfig(CoreConfiguration.class).getDisabledInstrumentations()).thenReturn(Collections.singletonList("test"));
        init(config);
        assertThat(interceptMe()).isEmpty();
    }

    @Test
    void testDontInstrumentOldClassFileVersions() {
        ElasticApmAgent.initInstrumentation(MockTracer.create(),
            ByteBuddyAgent.install(),
            Collections.singletonList(new MathInstrumentation()));
        // if the instrumentation applied, it would return 42
        // but instrumenting old class file versions could lead to VerifyErrors in some cases and possibly some more shenanigans
        // so we we are better off not touching Java 1.4 code at all
        assertThat(MathUtils.sign(-42)).isEqualTo(-1);
    }

    private void init(ConfigurationRegistry config) {
        ElasticApmAgent.initInstrumentation(new ElasticApmTracerBuilder()
                .configurationRegistry(config)
                .build(),
            ByteBuddyAgent.install(),
            Collections.singletonList(new TestInstrumentation()));
    }

    private String interceptMe() {
        return "";
    }

    public static class TestInstrumentation extends ElasticApmInstrumentation {
        @Advice.OnMethodExit
        public static void onMethodExit(@Advice.Return(readOnly = false) String returnValue) {
            returnValue = "intercepted";
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return ElementMatchers.named("co.elastic.apm.bci.InstrumentationTest");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return ElementMatchers.named("interceptMe");
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Collections.singleton("test");
        }
    }

    public static class MathInstrumentation extends ElasticApmInstrumentation {
        @Advice.OnMethodExit
        public static void onMethodExit(@Advice.Return(readOnly = false) int returnValue) {
            returnValue = 42;
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return ElementMatchers.named("org.apache.commons.math.util.MathUtils");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return ElementMatchers.named("sign").and(ElementMatchers.takesArguments(int.class));
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Collections.emptyList();
        }
    }
}
