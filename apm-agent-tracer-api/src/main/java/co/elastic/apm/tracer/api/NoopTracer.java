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
package co.elastic.apm.tracer.api;

import co.elastic.apm.tracer.api.dispatch.BinaryHeaderGetter;
import co.elastic.apm.tracer.api.dispatch.TextHeaderGetter;
import co.elastic.apm.tracer.api.pooling.DisabledObjectPoolFactory;
import co.elastic.apm.tracer.api.pooling.ObjectPoolFactory;
import co.elastic.apm.tracer.api.service.ServiceInfo;

import javax.annotation.Nullable;

class NoopTracer implements Tracer {

    static final Tracer INSTANCE = new NoopTracer();

    private NoopTracer() {
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Nullable
    @Override
    public <T extends Tracer> T probe(Class<T> type) {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Tracer> T require(Class<T> type) {
        if (type == Tracer.class) {
            return (T) this;
        }
        throw new IllegalStateException();
    }

    @Override
    public <T> T getConfig(Class<T> configuration) {
        throw new IllegalStateException("Configuration not available: " + configuration);
    }

    @Override
    public ObjectPoolFactory getObjectPoolFactory() {
        return DisabledObjectPoolFactory.INSTANCE;
    }

    @Nullable
    @Override
    public Transaction<?> startRootTransaction(@Nullable ClassLoader initiatingClassLoader) {
        return null;
    }

    @Nullable
    @Override
    public <C> Transaction<?> startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return null;
    }

    @Nullable
    @Override
    public <C> Transaction<?> startChildTransaction(@Nullable C headerCarrier, BinaryHeaderGetter<C> binaryHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return null;
    }

    @Nullable
    @Override
    public Span<?> createExitChildSpan() {
        return null;
    }

    @Nullable
    @Override
    public AbstractSpan<?> getActive() {
        return null;
    }

    @Nullable
    @Override
    public Transaction<?> currentTransaction() {
        return null;
    }

    @Nullable
    @Override
    public Span<?> getActiveSpan() {
        return null;
    }

    @Nullable
    @Override
    public Span<?> getActiveExitSpan() {
        return null;
    }

    @Override
    public void endTransaction(Transaction<?> transaction) {
    }

    @Override
    public void endSpan(Span<?> span) {
    }
}
