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
package co.elastic.apm.agent.awssdk.v1.helper.sqs.wrapper;

import co.elastic.apm.agent.tracer.Tracer;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;

import java.util.Collection;
import java.util.List;

public class ReceiveMessageResultWrapper extends ReceiveMessageResult {
    private final ReceiveMessageResult delegate;
    private final Tracer tracer;
    private final String queueName;

    private final MessageListWrapper listWrapper;

    public ReceiveMessageResultWrapper(ReceiveMessageResult delegate, Tracer tracer, String queueName) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.queueName = queueName;
        this.listWrapper = new MessageListWrapper(delegate.getMessages(), tracer, queueName);
    }

    public List<Message> getMessages() {
        this.listWrapper.updateDelegate(delegate.getMessages());
        return this.listWrapper;
    }

    public void setMessages(Collection<Message> messages) {
        delegate.setMessages(messages);
    }

    public ReceiveMessageResult withMessages(Message... messages) {
        delegate.withMessages(messages);
        return this;
    }

    public ReceiveMessageResult withMessages(Collection<Message> messages) {
        delegate.withMessages(messages);
        return this;
    }

    public String toString() {
        return delegate.toString();
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    public int hashCode() {
        return delegate.hashCode();
    }

    public ReceiveMessageResult clone() {
        return new ReceiveMessageResultWrapper(delegate.clone(), tracer, queueName);
    }
}
