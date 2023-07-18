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
package co.elastic.apm.agent.sdk.internal.pooling;

import java.util.concurrent.Callable;

public class NonPoolingPoolFactory implements ObjectPooling.ObjectPoolFactory {

    private static class NoopHandle<T> implements ObjectHandle<T> {

        private final T value;

        private NoopHandle(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public void close() {
        }
    }

    @Override
    public <T> ObjectPool<NoopHandle<T>> createHandlePool(Callable<T> allocator) {
        return new ObjectPool<NoopHandle<T>>() {
            @Override
            public NoopHandle<T> createInstance() {
                try {
                    return new NoopHandle<>(allocator.call());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
