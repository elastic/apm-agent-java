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

import co.elastic.apm.agent.tracer.pooling.Recyclable;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractContext implements Recyclable, co.elastic.apm.agent.tracer.AbstractContext {

    public static final String REDACTED_CONTEXT_STRING = "[REDACTED]";

    /**
     * A flat mapping of user-defined labels with {@link String} keys and {@link String}, {@link Number} or {@link Boolean} values
     * (formerly known as tags).
     * <p>
     * See also https://github.com/elastic/ecs#-base-fields
     * </p>
     */
    private final Map<String, Object> labels = new ConcurrentHashMap<>();

    /**
     * An object containing contextual data for Messages (incoming in case of transactions or outgoing in case of spans)
     */
    private final Message message = new Message();

    public Iterator<? extends Map.Entry<String, ?>> getLabelIterator() {
        return labels.entrySet().iterator();
    }

    public void addLabel(String key, String value) {
        labels.put(key, value);
    }

    public void addLabel(String key, Number value) {
        labels.put(key, value);
    }

    public void addLabel(String key, boolean value) {
        labels.put(key, value);
    }

    @Nullable
    public Object getLabel(String key) {
        return labels.get(key);
    }

    public void clearLabels() {
        labels.clear();
    }

    public boolean hasLabels() {
        return !labels.isEmpty();
    }

    @Override
    public Message getMessage() {
        return message;
    }

    @Override
    public void resetState() {
        labels.clear();
        message.resetState();
    }

    public boolean hasContent() {
        return !labels.isEmpty() || message.hasContent();
    }

    public void copyFrom(AbstractContext other) {
        labels.putAll(other.labels);
        message.copyFrom(other.message);
    }
}
