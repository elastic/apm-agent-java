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
package co.elastic.apm.agent.embeddedotel.proxy;

import io.opentelemetry.api.common.AttributeKey;

import java.util.List;
import java.util.Objects;

public class ProxyAttributeKey<T> {

    private final AttributeKey<T> delegate;

    public ProxyAttributeKey(AttributeKey<T> delegate) {
        this.delegate = delegate;
    }


    public AttributeKey<T> getDelegate() {
        return delegate;
    }

    public static ProxyAttributeKey<String> stringKey(String key) {
        return new ProxyAttributeKey<>(AttributeKey.stringKey(key));
    }

    public static ProxyAttributeKey<Boolean> booleanKey(String key) {
        return new ProxyAttributeKey<>(AttributeKey.booleanKey(key));
    }

    public static ProxyAttributeKey<Long> longKey(String key) {
        return new ProxyAttributeKey<>(AttributeKey.longKey(key));
    }

    public static ProxyAttributeKey<Double> doubleKey(String key) {
        return new ProxyAttributeKey<>(AttributeKey.doubleKey(key));
    }

    public static ProxyAttributeKey<List<String>> stringArrayKey(String key) {
        return new ProxyAttributeKey<>(AttributeKey.stringArrayKey(key));
    }

    public static ProxyAttributeKey<List<Boolean>> booleanArrayKey(String key) {
        return new ProxyAttributeKey<>(AttributeKey.booleanArrayKey(key));
    }

    public static ProxyAttributeKey<List<Long>> longArrayKey(String key) {
        return new ProxyAttributeKey<>(AttributeKey.longArrayKey(key));
    }

    public static ProxyAttributeKey<List<Double>> doubleArrayKey(String key) {
        return new ProxyAttributeKey<>(AttributeKey.doubleArrayKey(key));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProxyAttributeKey<?> that = (ProxyAttributeKey<?>) o;
        return Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return delegate != null ? delegate.hashCode() : 0;
    }
}
