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
package co.elastic.apm.agent.tracer.configuration;

import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.tracer.configuration.WildcardMatcherValueConverter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.converter.ListValueConverter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MessagingConfiguration extends ConfigurationOptionProvider {
    private static final String MESSAGING_CATEGORY = "Messaging";
    private static final String MESSAGE_POLLING_TRANSACTION_STRATEGY = "message_polling_transaction_strategy";
    private static final String MESSAGE_BATCH_STRATEGY = "message_batch_strategy";

    private ConfigurationOption<JmsStrategy> messagePollingTransactionStrategy = ConfigurationOption.enumOption(JmsStrategy.class)
        .key(MESSAGE_POLLING_TRANSACTION_STRATEGY)
        .configurationCategory(MESSAGING_CATEGORY)
        .tags("internal")
        .description("Determines whether the agent should create transactions for the polling action itself (e.g. `javax.jms.MessageConsumer#receive`), \n" +
            "attempt to create a transaction for the message handling code occurring if the polling method returns a message, \n" +
            "or both. Valid options are: `POLLING`, `HANDLING` and `BOTH`. \n" +
            "\n" +
            "This option is case-insensitive and is only relevant for JMS.")
        .dynamic(true)
        .buildWithDefault(JmsStrategy.HANDLING);

    private ConfigurationOption<BatchStrategy> messageBatchStrategy = ConfigurationOption.enumOption(BatchStrategy.class)
        .key(MESSAGE_BATCH_STRATEGY)
        .configurationCategory(MESSAGING_CATEGORY)
        .tags("internal")
        .description("Determines whether Spring messaging system libraries should create a batch for the processing of the entire \n" +
            "message/record batch, or one transaction for each message/record processing, typically by wrapping the message batch data \n" +
            "structure. Valid options are `SINGLE_HANDLING` and `BATCH_HANDLING`. \n" +
            "\n" +
            "This option is case-insensitive and is only relevant for Spring messaging system libraries that support batch processing.")
        .dynamic(true)
        .buildWithDefault(BatchStrategy.BATCH_HANDLING);

    private ConfigurationOption<Boolean> collectQueueAddress = ConfigurationOption.booleanOption()
        .key("collect_queue_address")
        .configurationCategory(MESSAGING_CATEGORY)
        .tags("internal")
        .description("Determines whether the agent should collect destination address and port, as this may be \n" +
            "an expensive operation.")
        .dynamic(true)
        .buildWithDefault(Boolean.TRUE);

    private final ConfigurationOption<List<WildcardMatcher>> ignoreMessageQueues = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("ignore_message_queues")
        .configurationCategory(MESSAGING_CATEGORY)
        .description("Used to filter out specific messaging queues/topics from being traced. \n" +
            "\n" +
            "This property should be set to an array containing one or more strings.\n" +
            "When set, sends-to and receives-from the specified queues/topic will be ignored.\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION)
        .dynamic(true)
        .buildWithDefault(Collections.<WildcardMatcher>emptyList());

    private final ConfigurationOption<Boolean> endMessagingTransactionOnPoll = ConfigurationOption.booleanOption()
        .key("end_messaging_transaction_on_poll")
        .configurationCategory(MESSAGING_CATEGORY)
        .tags("internal")
        .description("When tracing messaging systems, we sometimes create transactions based on consumed messages \n" +
            "when they are iterated-over after being polled. This means that transaction ending relies on the \n" +
            "iterating behavior, which means transactions may be left unclosed. In such cases, we deactivate \n" +
            "and close such transactions when the next poll action is invoked on the same thread. \n" +
            "However, if the messaging transaction itself tries to poll a queue, it will be ended prematurely. In \n" +
            "such cases, set this property to false." +
            WildcardMatcher.DOCUMENTATION)
        .dynamic(true)
        .buildWithDefault(Boolean.TRUE);

    private final ConfigurationOption<Collection<String>> jmsListenerPackages = ConfigurationOption
        .stringsOption()
        .key("jms_listener_packages")
        .tags("performance", "added[1.36.0]")
        .configurationCategory(MESSAGING_CATEGORY)
        .description("Defines which packages contain JMS MessageListener implementations for instrumentation." +
            "\n" +
            "When set to a non-empty value, only the classes matching configuration will be instrumented.\n" +
            "This configuration option helps to make MessageListener type matching faster and improve application startup performance."
        )
        .dynamic(false)
        .buildWithDefault(Collections.<String>emptyList());

    public JmsStrategy getMessagePollingTransactionStrategy() {
        return messagePollingTransactionStrategy.get();
    }

    public BatchStrategy getMessageBatchStrategy() {
        return messageBatchStrategy.get();
    }

    public List<WildcardMatcher> getIgnoreMessageQueues() {
        return ignoreMessageQueues.get();
    }

    public boolean shouldCollectQueueAddress() {
        return collectQueueAddress.get();
    }

    public boolean shouldEndMessagingTransactionOnPoll() {
        return endMessagingTransactionOnPoll.get();
    }

    public Collection<String> getJmsListenerPackages() {
        return jmsListenerPackages.get();
    }

    public enum JmsStrategy {
        /**
         * Create a transaction capturing JMS {@code receive} invocations
         */
        POLLING,
        /**
         * Use heuristics to create a transaction that captures the JMS message handling execution. This strategy requires heuristics
         * when JMS {@code receive} APIs are used (rather than {@code onMessage}), as there is no API representing message handling start
         * and end. Even though this is riskier and less deterministic, it is the default JMS tracing strategy otherwise all
         * "interesting" subsequent events that follow message receive will be missed because there will be no active transaction.
         */
        HANDLING,
        /**
         * Create a transaction both for the polling ({@code receive}) action AND the subsequent message handling.
         */
        BOTH
    }

    /**
     * Only relevant for Spring wrappers around supported messaging clients, such as AMQP.
     */
    public enum BatchStrategy {
        /**
         * Create a transaction for each received message/record, typically by wrapping the message batch data structure
         */
        SINGLE_HANDLING,
        /**
         * Create a single transaction encapsulating the entire message/record batch-processing.
         */
        BATCH_HANDLING
    }
}
