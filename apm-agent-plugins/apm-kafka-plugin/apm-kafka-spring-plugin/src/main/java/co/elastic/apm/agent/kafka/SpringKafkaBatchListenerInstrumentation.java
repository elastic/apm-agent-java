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
package co.elastic.apm.agent.kafka;

import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.util.LoggerUtils;
import co.elastic.apm.agent.util.PrivilegedActionUtils;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Spring internally does the batch processing in two steps:
 * 1. Iterate over all Messages and store them in a List
 * 2. Pass the list to the registered batch KafkaListener
 * <p>
 * In order to catch both operations in a single transaction with span links,
 * this instrumentation creates a transaction which spans both of the actions listed above.
 * <p>
 * This instrumentation instruments {@link org.springframework.kafka.listener.KafkaMessageListenerContainer.ListenerConsumer#invokeBatchListener(org.apache.kafka.clients.consumer.ConsumerRecords)}.
 */
@SuppressWarnings("JavadocReference")
public class SpringKafkaBatchListenerInstrumentation extends BaseKafkaInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.springframework.kafka.listener.KafkaMessageListenerContainer$ListenerConsumer");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("invokeBatchListener")
            .and(takesArguments(1))
            .and(takesArgument(0, named("org.apache.kafka.clients.consumer.ConsumerRecords")));
    }

    @Override
    public String getAdviceClassName() {
        return getClass().getName() + "$SpringKafkaBatchListenerAdvice";
    }

    public static class SpringKafkaBatchListenerAdvice {

        private static final Logger oneTimeTransactionCreationWarningLogger;

        static {
            oneTimeTransactionCreationWarningLogger = LoggerUtils.logOnce(LoggerFactory.getLogger("Spring-Kafka-Batch-Logger"));
        }

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.This Object thiz) {
            Transaction<?> transaction = tracer.startRootTransaction(PrivilegedActionUtils.getClassLoader(thiz.getClass()));
            if (transaction != null) {
                transaction
                    .withType("messaging")
                    .withName("Spring Kafka Message Batch Processing")
                    .activate();
                transaction.setFrameworkName("Kafka");
            } else {
                oneTimeTransactionCreationWarningLogger.warn("Failed to start Spring Kafka transaction for batch processing");
            }
            //we don't need to add span links here, they will be added by the KafkaConsumerRecordsInstrumentation
            return transaction;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Nullable @Advice.Enter Object transactionObj) {
            Transaction<?> transaction = (Transaction<?>) transactionObj;
            if (transaction != null) {
                transaction.deactivate().end();
            }
        }

    }

}
