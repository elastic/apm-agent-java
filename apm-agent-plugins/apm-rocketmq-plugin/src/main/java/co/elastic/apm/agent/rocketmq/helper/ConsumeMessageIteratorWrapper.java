/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.rocketmq.helper;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Iterator;

class ConsumeMessageIteratorWrapper implements Iterator<MessageExt> {

    private static final Logger logger = LoggerFactory.getLogger(ConsumeMessageIteratorWrapper.class);

    private final Iterator<MessageExt> delegate;

    private final RocketMQInstrumentationHelperImpl helper;

    ConsumeMessageIteratorWrapper(Iterator<MessageExt> delegate, RocketMQInstrumentationHelperImpl helper) {
        this.delegate = delegate;
        this.helper = helper;
    }

    @Override
    public boolean hasNext() {
        endCurrentMessagingTransaction();
        return delegate.hasNext();
    }

    @Override
    public MessageExt next() {
        endCurrentMessagingTransaction();
        MessageExt messageExt = delegate.next();
        helper.onConsumeStart(messageExt);
        return messageExt;
    }

    @Override
    public void remove() {
        delegate.remove();
    }

    private void endCurrentMessagingTransaction() {
        try {
            Transaction transaction = helper.getTracer().currentTransaction();
            if (transaction != null && "messaging".equals(transaction.getType())) {
                transaction.deactivate().end();
            }
        } catch (Exception e) {
            logger.error("Error in RocketMQ iterator wrapper", e);
        }
    }

}
