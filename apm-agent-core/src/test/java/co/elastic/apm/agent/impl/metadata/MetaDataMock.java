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
package co.elastic.apm.agent.impl.metadata;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.spy;

public class MetaDataMock {

    /**
     * Creates a metadata mock based on the given info synchronously
     *
     * @return a mock future, already containing the medata info
     */
    public static Future<MetaData> create(ProcessInfo process, Service service, SystemInfo system, @Nullable CloudProviderInfo cloudProviderInfo,
                                          Map<String, String> globalLabels, @Nullable FaaSMetaDataExtension faaSMetaDataExtension) {
        return create(new MetaData(process, service, system, cloudProviderInfo, globalLabels, faaSMetaDataExtension));
    }

    /**
     * Creates a metadata with default mock data
     *
     * @return a mock future, already containing the medata info
     */
    public static Future<MetaData> create() {
        return create(new MetaData(
            new ProcessInfo("test-process"),
            new Service(),
            new SystemInfo("x64", "localhost", null, "platform"),
            null,
            Collections.emptyMap(),
            null
        ));
    }

    public static Future<MetaData> create(MetaData value) {
        return new NoWaitFuture<>(value);
    }

    /**
     * @return a metadata spy with some default values
     */
    public static MetaData createDefaultMock() {
        return spy(new MetaData(
            new ProcessInfo("test-process"),
            new Service(),
            new SystemInfo("x64", "localhost", null, "platform"),
            null,
            Collections.emptyMap(),
            null));
    }

    private static class NoWaitFuture<V> implements Future<V> {

        @Nullable
        private final V value;

        NoWaitFuture(@Nullable V value) {
            this.value = value;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Nullable
        @Override
        public V get() {
            return value;
        }

        @Nullable
        @Override
        public V get(long timeout, TimeUnit unit) {
            return value;
        }
    }
}
