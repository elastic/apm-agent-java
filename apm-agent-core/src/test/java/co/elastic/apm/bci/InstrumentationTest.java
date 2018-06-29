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

import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.ElasticApmTracerBuilder;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

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
        public String getInstrumentationGroupName() {
            return "test";
        }
    }
}
