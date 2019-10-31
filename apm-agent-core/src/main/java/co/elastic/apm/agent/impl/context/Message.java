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
package co.elastic.apm.agent.impl.context;

import co.elastic.apm.agent.objectpool.Recyclable;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.impl.context.AbstractContext.REDACTED_CONTEXT_STRING;

public class Message implements Recyclable {

    @Nullable
    private String queueName;

    @Nullable
    private String topicName;

    @Nullable
    private String body;

    @Nullable
    public String getQueueName() {
        return queueName;
    }

    public Message withQueue(String queueName) {
        this.queueName = queueName;
        return this;
    }

    @Nullable
    public String getTopicName() {
        return topicName;
    }

    public Message withTopic(String topicName) {
        this.topicName = topicName;
        return this;
    }

    @Nullable
    public String getBody() {
        return body;
    }

    public Message withBody(@Nullable String body) {
        this.body = body;
        return this;
    }

    public void redactBody() {
        body = REDACTED_CONTEXT_STRING;
    }

    public boolean hasContent() {
        return queueName != null || topicName != null || body != null;
    }

    @Override
    public void resetState() {
        queueName = null;
        topicName = null;
        body = null;
    }

    public void copyFrom(Message other) {
        this.queueName = other.getQueueName();
        this.topicName = other.getTopicName();
        this.body = other.body;
    }
}
