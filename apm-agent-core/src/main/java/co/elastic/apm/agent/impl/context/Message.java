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
import co.elastic.apm.agent.util.BinaryHeaderMap;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.impl.context.AbstractContext.REDACTED_CONTEXT_STRING;

public class Message implements Recyclable {

    @Nullable
    private String queueName;

    @Nullable
    private String topicName;

    private final StringBuilder body = new StringBuilder();

    /**
     * Represents the message age in milliseconds. Since 0 is a valid value (can occur due to clock skews between
     * sender and receiver) - a negative value represents invalid or unavailable age.
     */
    private long age = -1L;

    /**
     * A mapping of message headers (in JMS includes properties as well)
     */
    private final Headers headers = new Headers();

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

    public StringBuilder getBody() {
        return body;
    }

    public Message withBody(@Nullable String body) {
        this.body.setLength(0);
        this.body.append(body);
        return this;
    }

    public Message appendToBody(CharSequence bodyContent) {
        this.body.append(bodyContent);
        return this;
    }

    public void redactBody() {
        body.setLength(0);
        body.append(REDACTED_CONTEXT_STRING);
    }

    public Message addHeader(String key, String value) {
        headers.add(key, value);
        return this;
    }

    public Message addHeader(String key, byte[] value) throws BinaryHeaderMap.InsufficientCapacityException {
        headers.add(key, value);
        return this;
    }

    public long getAge() {
        return age;
    }

    public Message withAge(long age) {
        this.age = age;
        return this;
    }

    public Headers getHeaders() {
        return headers;
    }

    public boolean hasContent() {
        return queueName != null || topicName != null || body.length() > 0 || headers.size() > 0;
    }

    @Override
    public void resetState() {
        queueName = null;
        topicName = null;
        body.setLength(0);
        headers.resetState();
        age = -1L;
    }

    public void copyFrom(Message other) {
        this.queueName = other.getQueueName();
        this.topicName = other.getTopicName();
        this.body.setLength(0);
        this.body.append(other.body);
        this.headers.copyFrom(other.getHeaders());
        this.age = other.getAge();
    }
}
