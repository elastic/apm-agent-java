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
