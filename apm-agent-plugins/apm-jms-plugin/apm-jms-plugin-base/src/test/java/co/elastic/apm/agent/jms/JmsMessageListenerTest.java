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
package co.elastic.apm.agent.jms;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.jms.test.TestMessageConsumer;
import co.elastic.apm.agent.jms.test.TestMessageListener;
import co.elastic.apm.agent.jms.test.TestMsgHandler;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.jms.Message;
import javax.jms.MessageListener;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

// does not inherit from AbstractInstrumentationTest because the type pre-filter is applied early and can't be changed
// at runtime, thus an isolated agent setup is required
public class JmsMessageListenerTest {

    private ElasticApmTracer tracer;
    private ConfigurationRegistry config;

    @BeforeEach
    public void before(){
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup();
        tracer = mockInstrumentationSetup.getTracer();
        config = mockInstrumentationSetup.getConfig();
        assertThat(tracer.isRunning()).isTrue();
    }

    @AfterEach
    public void after() {
        tracer.stop();
        ElasticApmAgent.reset();
    }

    private void startAgent(){
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
    }

    @Test
    public void testJmsMessageListenerPackage_defaultConfig() throws Exception {
        startAgent();

        // default configuration
        testJmsMessageListenerPackage(true, JmsMessageListenerVariant.MATCHING_NAME_CONVENTION);
        testJmsMessageListenerPackage(true, JmsMessageListenerVariant.INNER_CLASS);
        testJmsMessageListenerPackage(true, JmsMessageListenerVariant.LAMBDA);
        testJmsMessageListenerPackage(false, JmsMessageListenerVariant.NOT_MATCHING_NAME_CONVENTION);
    }

    @Test
    public void testJmsMessageListenerPackage_customValue() throws Exception {
        doReturn(Arrays.asList("co.elastic.apm.agent.jms.test")).when(config.getConfig(MessagingConfiguration.class)).getJmsListenerPackages();

        startAgent();

        testJmsMessageListenerPackage(true, JmsMessageListenerVariant.MATCHING_NAME_CONVENTION);
        testJmsMessageListenerPackage(true, JmsMessageListenerVariant.INNER_CLASS);
        testJmsMessageListenerPackage(true, JmsMessageListenerVariant.LAMBDA);
        testJmsMessageListenerPackage(true, JmsMessageListenerVariant.NOT_MATCHING_NAME_CONVENTION);
    }

    private enum JmsMessageListenerVariant {
        MATCHING_NAME_CONVENTION,
        INNER_CLASS,
        LAMBDA,
        NOT_MATCHING_NAME_CONVENTION
    }

    private void testJmsMessageListenerPackage(boolean expectIncluded, JmsMessageListenerVariant variant) throws Exception {

        assertThat(tracer.currentTransaction())
            .describedAs("no active transaction expected before onMessage")
            .isNull();

        Message message = mock(Message.class);

        AtomicReference<Transaction> transaction = new AtomicReference<>();

        switch (variant){
            case MATCHING_NAME_CONVENTION:
                // name fits the naming convention
                new TestMessageListener(transaction).onMessage(message);
                break;
            case NOT_MATCHING_NAME_CONVENTION:
                new TestMsgHandler(transaction).onMessage(message);
                break;
            case INNER_CLASS:
                new InnerClassMsgHandler(transaction).onMessage(message);
                break;
            case LAMBDA:
                // lambda is instrumented through wrapping on the message consumer
                MessageListener listener = msg -> {
                    transaction.set(GlobalTracer.get().require(Tracer.class).currentTransaction());
                };
                TestMessageConsumer consumer = new TestMessageConsumer();
                // listener should be wrapped on setter entry
                consumer.setMessageListener(listener);
                // thus the wrapped listener is returned on getter
                consumer.getMessageListener().onMessage(message);
                break;
            default:
                throw new IllegalArgumentException("unknown case " + variant);
        }

        assertThat(tracer.currentTransaction())
            .describedAs("no active transaction expected after onMessage")
            .isNull();

        if (expectIncluded) {
            assertThat(transaction.get())
                .describedAs("transaction should be created for scenario " + variant)
                .isNotNull();
        } else {
            assertThat(transaction.get())
                .describedAs("transaction should not be created for scenario " + variant)
                .isNull();
        }
    }

    // will be instrumented by default as it's an inner class
    private static class InnerClassMsgHandler implements MessageListener {

        private final AtomicReference<Transaction> transaction;

        public InnerClassMsgHandler(AtomicReference<Transaction> transaction) {
            this.transaction = transaction;
        }

        @Override
        public void onMessage(Message message) {
            transaction.set(GlobalTracer.get().require(Tracer.class).currentTransaction());
        }
    }

}
