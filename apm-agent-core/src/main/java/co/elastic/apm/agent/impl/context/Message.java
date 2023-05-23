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
package co.elastic.apm.agent.impl.context;

import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.objectpool.Resetter;
import co.elastic.apm.agent.tracer.pooling.Allocator;
import co.elastic.apm.agent.tracer.pooling.Recyclable;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.impl.context.AbstractContext.REDACTED_CONTEXT_STRING;

public class Message implements Recyclable, co.elastic.apm.agent.tracer.metadata.Message {

    private static final ObjectPool<StringBuilder> stringBuilderPool = QueueBasedObjectPool.of(new MpmcAtomicArrayQueue<StringBuilder>(128), false,
        new Allocator<StringBuilder>() {
            @Override
            public StringBuilder createInstance() {
                return new StringBuilder();
            }
        },
        new Resetter<StringBuilder>() {
            @Override
            public void recycle(StringBuilder object) {
                object.setLength(0);
            }
        });

    @Nullable
    private String queueName;

    @Nullable
    private StringBuilder body;

    @Nullable
    private String routingKey;

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

    @Override
    public Message withQueue(@Nullable String queueName) {
        this.queueName = queueName;
        return this;
    }

    @Override
    public Message withRoutingKey(String routingKey) {
        this.routingKey = routingKey;
        return this;
    }

    @Nullable
    public String getRoutingKey() {
        return routingKey;
    }

    /**
     * Gets a body StringBuilder to write content to. If this message's body is not initializes, this method will
     * initialize from the StringBuilder pool.
     *
     * @return a StringBuilder to write body content to. Never returns null.
     */
    public StringBuilder getBodyForWrite() {
        if (body == null) {
            body = stringBuilderPool.createInstance();
        }
        return body;
    }

    /**
     * @return a body if already initialized, null otherwise
     */
    @Nullable
    public StringBuilder getBodyForRead() {
        return body;
    }

    @Override
    public Message withBody(@Nullable String body) {
        StringBuilder thisBody = getBodyForWrite();
        thisBody.setLength(0);
        thisBody.append(body);
        return this;
    }

    @Override
    public Message appendToBody(CharSequence bodyContent) {
        getBodyForWrite().append(bodyContent);
        return this;
    }

    public void redactBody() {
        if (body != null && body.length() > 0) {
            body.setLength(0);
            body.append(REDACTED_CONTEXT_STRING);
        }
    }

    @Override
    public Message addHeader(@Nullable String key, @Nullable String value) {
        headers.add(key, value);
        return this;
    }

    @Override
    public Message addHeader(@Nullable String key, @Nullable byte[] value) {
        headers.add(key, value);
        return this;
    }

    public long getAge() {
        return age;
    }

    @SuppressWarnings("UnusedReturnValue")
    @Override
    public Message withAge(long age) {
        this.age = age;
        return this;
    }

    public Headers getHeaders() {
        return headers;
    }

    public boolean hasContent() {
        return queueName != null || (body != null && body.length() > 0) || headers.size() > 0;
    }

    @Override
    public void resetState() {
        queueName = null;
        headers.resetState();
        age = -1L;
        if (body != null) {
            stringBuilderPool.recycle(body);
            body = null;
        }
        routingKey = null;
    }

    public void copyFrom(Message other) {
        resetState();
        this.queueName = other.getQueueName();
        if (other.body != null) {
            getBodyForWrite().append(other.body);
        }
        this.headers.copyFrom(other.getHeaders());
        this.age = other.getAge();
        this.routingKey = other.getRoutingKey();
    }
}
