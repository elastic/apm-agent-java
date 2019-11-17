/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.jms;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;

import javax.annotation.Nullable;

@VisibleForAdvice
public interface JmsInstrumentationHelper<D, M, L> {

    /**
     * In some cases, dashes are not allowed in JMS Message property names
     */
    String JMS_TRACE_PARENT_PROPERTY = TraceContext.TRACE_PARENT_HEADER.replace('-', '_');

    /**
     * When the agent computes a destination name instead of using the default queue name- it should be passed as a
     * message property, in case the receiver side cannot apply the same computation. For example, temporary queues are
     * identified based on the queue type and all receive the same generic name. In Artemis Active MQ, the queue
     * generated at the receiver side is not of the temporary type, so this name computation cannot be made.
     */
    String JMS_DESTINATION_NAME_PROPERTY = "elastic_apm_dest_name";

    /**
     * Indicates a transaction is created for the message handling flow, but should not be used as the actual type of
     * reported transactions.
     */
    String MESSAGE_HANDLING = "message-handling";

    /**
     * Indicates a transaction is created for a message polling method, but should not be used as the actual type of
     * reported transactions.
     */
    String MESSAGE_POLLING = "message-polling";

    String MESSAGING_TYPE = "messaging";

    String RECEIVE_NAME_PREFIX = "JMS RECEIVE";

    // JMS known headers
    //----------------------
    String JMS_MESSAGE_ID_HEADER = "JMSMessageID";
    String JMS_EXPIRATION_HEADER = "JMSExpiration";
    String JMS_TIMESTAMP_HEADER = "JMSTimestamp";

    @Nullable
    Span startJmsSendSpan(D destination, M message);

    @Nullable
    L wrapLambda(@Nullable L listener);

    @Nullable
    String extractDestinationName(@Nullable M message, D destination);

    boolean ignoreDestination(@Nullable String destinationName);

    void addDestinationDetails(M message, D destination, String destinationName, AbstractSpan span);

    void addMessageDetails(@Nullable M message, AbstractSpan span);
}
