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

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.springframework.amqp.core.Message;

import java.util.List;

public class MessageBatchHelper {

    public static final Logger logger = LoggerFactory.getLogger(MessageBatchHelper.class);

    private final ElasticApmTracer tracer;
    private final SpringAmqpTransactionHelper transactionHelper;

    public MessageBatchHelper(ElasticApmTracer tracer, SpringAmqpTransactionHelper transactionHelper) {
        this.tracer = tracer;
        this.transactionHelper = transactionHelper;
    }

    public List<Message> wrapMessageBatchList(List<Message> messageBatchList) {
        try {
            return new MessageBatchListWrapper(messageBatchList, tracer, transactionHelper);
        } catch (Throwable throwable) {
            logger.debug("Failed to wrap Spring AMQP MessageListener list", throwable);
            return messageBatchList;
        }
    }
}
