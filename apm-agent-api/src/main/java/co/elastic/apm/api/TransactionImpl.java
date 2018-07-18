/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.api;

import javax.annotation.Nonnull;

/**
 * If the agent is active, it injects the implementation from
 * co.elastic.apm.plugin.api.TransactionInstrumentation
 * into this class.
 * <p>
 * Otherwise, this class is a noop.
 * </p>
 */
class TransactionImpl implements Transaction {

    @Nonnull
    @SuppressWarnings("unused")
    private final Object transaction;

    TransactionImpl(@Nonnull Object transaction) {
        this.transaction = transaction;
    }

    @Override
    public void setName(String name) {
        // co.elastic.apm.plugin.api.TransactionInstrumentation$SetNameInstrumentation.setName
    }

    @Override
    public void setType(String type) {
        // co.elastic.apm.plugin.api.TransactionInstrumentation$SetTypeInstrumentation.setType
    }

    @Override
    public void addTag(String key, String value) {
        // co.elastic.apm.plugin.api.TransactionInstrumentation$AddTagInstrumentation.addTag
    }

    @Override
    public void setUser(String id, String email, String username) {
        // co.elastic.apm.plugin.api.TransactionInstrumentation$SetUserInstrumentation.setUser
    }

    @Override
    public void end() {
        // co.elastic.apm.plugin.api.TransactionInstrumentation$EndInstrumentation.end
    }

    @Override
    public Span createSpan() {
        Object span = doCreateSpan();
        return span != null ? new SpanImpl(span) : NoopSpan.INSTANCE;
    }

    private Object doCreateSpan() {
        // co.elastic.apm.plugin.api.TransactionInstrumentation$DoCreateSpanInstrumentation.doCreateSpan
        return null;
    }
}
