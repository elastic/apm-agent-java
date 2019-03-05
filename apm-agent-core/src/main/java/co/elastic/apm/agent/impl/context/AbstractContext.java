/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.impl.context;

import co.elastic.apm.agent.objectpool.Recyclable;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractContext implements Recyclable {
    /**
     * A flat mapping of user-defined labels with {@link String} keys and {@link String}, {@link Number} or {@link Boolean} values
     * (formerly known as tags).
     * <p>
     * See also https://github.com/elastic/ecs#-base-fields
     * </p>
     */
    private final Map<String, Object> labels = new ConcurrentHashMap<>();

    public Iterator<? extends Map.Entry<String, ?>> getTagsIterator() {
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
    public void resetState() {
        labels.clear();
    }

    public boolean hasContent() {
        return !labels.isEmpty();
    }

    public void copyFrom(AbstractContext other) {
        labels.putAll(other.labels);
    }
}
