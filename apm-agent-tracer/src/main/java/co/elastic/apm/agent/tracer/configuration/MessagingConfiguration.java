package co.elastic.apm.agent.tracer.configuration;

import java.util.Collection;
import java.util.List;

public interface MessagingConfiguration {

    Collection<String> getJmsListenerPackages();

    JmsStrategy getMessagePollingTransactionStrategy();

    BatchStrategy getMessageBatchStrategy();

    boolean shouldEndMessagingTransactionOnPoll();

    List<Matcher> getIgnoreMessageQueues();

    boolean shouldCollectQueueAddress();

    enum JmsStrategy {
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
    enum BatchStrategy {
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
