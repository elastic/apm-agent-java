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
package co.elastic.apm.api;

import javax.annotation.Nonnull;

/**
 * If the agent is active, it injects the implementation from
 * co.elastic.apm.agent.plugin.api.TransactionInstrumentation
 * into this class.
 * <p>
 * Otherwise, this class is a noop.
 * </p>
 */
class TransactionImpl extends AbstractSpanImpl implements Transaction {

    TransactionImpl(@Nonnull Object transaction) {
        super(transaction);
    }

    @Nonnull
    @Override
    public Transaction setName(String name) {
        doSetName(name);
        return this;
    }

    @Nonnull
    @Override
    public Transaction setType(String type) {
        doSetType(type);
        return this;
    }

    @Nonnull
    @Override
    public Transaction addTag(String key, String value) {
        doAddTag(key, value);
        return this;
    }

    @Nonnull
    @Override
    public Transaction addLabel(String key, String value) {
        doAddStringLabel(key, value);
        return this;
    }

    @Nonnull
    @Override
    public Transaction addLabel(String key, Number value) {
        doAddNumberLabel(key, value);
        return this;
    }

    @Nonnull
    @Override
    public Transaction addLabel(String key, boolean value) {
        doAddBooleanLabel(key, value);
        return this;
    }

    @Nonnull
    @Override
    public Transaction addCustomContext(String key, String value) {
        // co.elastic.apm.agent.plugin.api.TransactionInstrumentation$AddCustomContextInstrumentation
        return this;
    }

    @Nonnull
    @Override
    public Transaction addCustomContext(String key, Number value) {
        // co.elastic.apm.agent.plugin.api.TransactionInstrumentation$AddCustomContextInstrumentation
        return this;
    }

    @Nonnull
    @Override
    public Transaction addCustomContext(String key, boolean value) {
        // co.elastic.apm.agent.plugin.api.TransactionInstrumentation$AddCustomContextInstrumentation
        return this;
    }

    @Override
    public Transaction setUser(String id, String email, String username) {
        // co.elastic.apm.agent.plugin.api.TransactionInstrumentation$SetUserInstrumentation.setUser
        return this;
    }

    @Override
    public Transaction setResult(String result) {
        // co.elastic.apm.agent.plugin.api.TransactionInstrumentation.SetResultInstrumentation
        return this;
    }

    @Nonnull
    @Override
    public String ensureParentId() {
        // co.elastic.apm.agent.plugin.api.TransactionInstrumentation.EnsureParentIdInstrumentation
        return "";
    }

    @Override
    public Transaction setStartTimestamp(long epochMicros) {
        doSetStartTimestamp(epochMicros);
        return this;
    }

}
