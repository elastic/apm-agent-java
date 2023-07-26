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
package co.elastic.apm.agent.jms.jakarta.test;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;

// no-op implementation that is required only for testing registration of lambda message listeners
public class TestMessageConsumer implements MessageConsumer {

    private MessageListener listener;

    @Override
    public String getMessageSelector() throws JMSException {
        return null;
    }

    @Override
    public MessageListener getMessageListener() throws JMSException {
        return listener;
    }

    @Override
    public void setMessageListener(MessageListener messageListener) throws JMSException {
        this.listener = messageListener;
    }

    @Override
    public Message receive() throws JMSException {
        return null;
    }

    @Override
    public Message receive(long l) throws JMSException {
        return null;
    }

    @Override
    public Message receiveNoWait() throws JMSException {
        return null;
    }

    @Override
    public void close() throws JMSException {

    }
}
