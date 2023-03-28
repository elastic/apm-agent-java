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
package co.elastic.apm.agent.rabbitmq;


import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.rabbitmq.header.SpringRabbitMQTextHeaderGetter;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.util.LoggerUtils;
import co.elastic.apm.agent.util.PrivilegedActionUtils;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import javax.annotation.Nullable;
import java.util.List;

import static net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class SpringAmqpBatchMessageListenerInstrumentation extends SpringBaseInstrumentation {
    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("onMessageBatch")
            .and(takesArgument(0, List.class)).and(isPublic());
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.rabbitmq.SpringAmqpBatchMessageListenerInstrumentation$MessageListenerContainerWrappingAdvice";
    }

    public static class MessageListenerContainerWrappingAdvice extends BaseAdvice {
        private static final Logger oneTimeTransactionCreationWarningLogger;
        private static final MessageBatchHelper messageBatchHelper;
        private static final MessagingConfiguration messagingConfiguration;

        static {
            Tracer tracer = GlobalTracer.get();
            messageBatchHelper = new MessageBatchHelper(tracer, transactionHelper);
            messagingConfiguration = tracer.getConfig(MessagingConfiguration.class);
            oneTimeTransactionCreationWarningLogger = LoggerUtils.logOnce(LoggerFactory.getLogger("Spring-AMQP-Batch-Logger"));
        }

        @Advice.AssignReturned.ToArguments(@ToArgument(index = 0, value = 0, typing = DYNAMIC))
        @Advice.OnMethodEnter(inline = false)
        public static Object[] beforeOnBatch(@Advice.This Object thiz,
                                             @Advice.Argument(0) @Nullable final List<Message> messageBatch) {

            List<Message> processedBatch = messageBatch;
            Transaction<?> batchTransaction = null;

            if (tracer.isRunning() && messageBatch != null && !messageBatch.isEmpty()) {
                AbstractSpan<?> active = tracer.getActive();
                if (active == null && messagingConfiguration.getMessageBatchStrategy() == MessagingConfiguration.BatchStrategy.BATCH_HANDLING) {
                    batchTransaction = tracer.startRootTransaction(PrivilegedActionUtils.getClassLoader(thiz.getClass()));
                    if (batchTransaction == null) {
                        oneTimeTransactionCreationWarningLogger.warn("Failed to start Spring AMQP transaction for batch processing");
                    } else {
                        batchTransaction.withType("messaging")
                            .withName("Spring AMQP Message Batch Processing")
                            .activate();
                    }
                } else {
                    oneTimeTransactionCreationWarningLogger.warn("Unexpected active span during batch message processing start: {}",
                        active);
                }

                active = tracer.getActive();
                if (active != null) {
                    for (Message message : messageBatch) {
                        MessageProperties messageProperties = message.getMessageProperties();
                        if (messageProperties != null) {
                            active.addLink(SpringRabbitMQTextHeaderGetter.INSTANCE, messageProperties);
                        }
                    }
                } else {
                    processedBatch = messageBatchHelper.wrapMessageBatchList(messageBatch);
                }
            }
            return new Object[]{processedBatch, batchTransaction};
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void afterOnBatch(@Advice.Enter @Nullable Object[] enter,
                                        @Advice.Thrown @Nullable Throwable throwable) {
            Transaction<?> batchTransaction = enter != null ? (Transaction<?>) enter[1] : null;
            if (batchTransaction != null) {
                try {
                    batchTransaction
                        .captureException(throwable)
                        .end();
                } finally {
                    batchTransaction.deactivate();
                }
            }
        }
    }
}
