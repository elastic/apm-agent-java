/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.spring.scheduled;

import java.util.Collections;

import org.awaitility.Duration;
import org.junit.BeforeClass;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import net.bytebuddy.agent.ByteBuddyAgent;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

/**
 * TODO <<Zweck und Verantwortung des Moduls, ggf. mehrere Zeilen>>
 */
@SpringJUnitConfig(ScheduledConfig.class)
class SpringScheduledTransactionNameInstrumentationTest {

    private static MockReporter reporter;
    private static ElasticApmTracer tracer;

    @BeforeClass
    @BeforeAll
    static void setUpAll() {
        reporter = new MockReporter();
        tracer = new ElasticApmTracerBuilder()
                .configurationRegistry(SpyConfiguration.createSpyConfig())
                .reporter(reporter)
                .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install(),
                Collections.singletonList(new SpringScheduledTransactionNameInstrumentation()));
    }

    @SpyBean
    private Counter counter;

    @Test
    void testScheduledAnnotatedMethodsAreTraced() {
        reporter.reset();
        await()
                .atMost(Duration.FIVE_HUNDRED_MILLISECONDS)
                .untilAsserted(() -> verify(counter, atLeast(5)).scheduled());
        assertThat(reporter.getTransactions().size(), greaterThanOrEqualTo(counter.getInvocationCount()));
        assertThat(reporter.getTransactions().get(0).getName().toString(), containsString("#scheduled"));
    }

}
