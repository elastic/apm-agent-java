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
package co.elastic.apm.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
    public Transaction setFrameworkName(String frameworkName) {
        // co.elastic.apm.agent.pluginapi.TransactionInstrumentation$SetFrameworkNameInstrumentation
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
    public Transaction setServiceInfo(String serviceName, String serviceVersion) {
        // co.elastic.apm.agent.pluginapi.TransactionInstrumentation$SetServiceInfoInstrumentation
        return this;
    }

    @Nonnull
    @Override
    public Transaction useServiceInfoForClassLoader(ClassLoader classLoader) {
        // co.elastic.apm.agent.pluginapi.TransactionInstrumentation$UseServiceInfoForClassLoaderInstrumentation
        return this;
    }

    @Nonnull
    @Deprecated
    @Override
    public Transaction addTag(String key, String value) {
        doAddTag(key, value);
        return this;
    }

    @Nonnull
    @Deprecated
    @Override
    public Transaction addLabel(String key, String value) {
        doAddStringLabel(key, value);
        return this;
    }

    @Nonnull
    @Deprecated
    @Override
    public Transaction addLabel(String key, Number value) {
        doAddNumberLabel(key, value);
        return this;
    }

    @Nonnull
    @Deprecated
    @Override
    public Transaction addLabel(String key, boolean value) {
        doAddBooleanLabel(key, value);
        return this;
    }

    @Nonnull
    @Override
    public Transaction setLabel(String key, String value) {
        doAddStringLabel(key, value);
        return this;
    }

    @Nonnull
    @Override
    public Transaction setLabel(String key, Number value) {
        doAddNumberLabel(key, value);
        return this;
    }

    @Nonnull
    @Override
    public Transaction setLabel(String key, boolean value) {
        doAddBooleanLabel(key, value);
        return this;
    }

    @Nonnull
    @Override
    public Transaction addCustomContext(String key, String value) {
        // co.elastic.apm.agent.pluginapi.TransactionInstrumentation$AddCustomContextInstrumentation
        return this;
    }

    @Nonnull
    @Override
    public Transaction addCustomContext(String key, Number value) {
        // co.elastic.apm.agent.pluginapi.TransactionInstrumentation$AddCustomContextInstrumentation
        return this;
    }

    @Nonnull
    @Override
    public Transaction addCustomContext(String key, boolean value) {
        // co.elastic.apm.agent.pluginapi.TransactionInstrumentation$AddCustomContextInstrumentation
        return this;
    }

    @Override
    public Transaction setUser(String id, String email, String username) {
        // co.elastic.apm.agent.pluginapi.TransactionInstrumentation$SetUserInstrumentation.setUser
        return this;
    }

    @Override
    public Transaction setUser(String id, String email, String username, String domain) {
        // co.elastic.apm.agent.pluginapi.TransactionInstrumentation$SetUserInstrumentation.setUser
        return this;
    }

    @Override
    public Transaction setRemoteAddress(String remoteAddress) {
        // co.elastic.apm.agent.pluginapi.TransactionInstrumentation$SetRemoteAddress.setRemoteAddress
        return this;
    }

    @Override
    public Transaction setResult(String result) {
        // co.elastic.apm.agent.pluginapi.TransactionInstrumentation.SetResultInstrumentation
        return this;
    }

    @Nonnull
    @Override
    public String ensureParentId() {
        // co.elastic.apm.agent.pluginapi.TransactionInstrumentation.EnsureParentIdInstrumentation
        return "";
    }

    @Override
    public Transaction setStartTimestamp(long epochMicros) {
        doSetStartTimestamp(epochMicros);
        return this;
    }

    @Override
    public Transaction setOutcome(Outcome outcome) {
        doSetOutcome(outcome);
        return this;
    }

    /**
     * @deprecated - used only for {@link co.elastic.apm.api.Span}
     */
    @Nonnull
    @Override
    @Deprecated
    public Transaction setDestinationAddress(@Nullable String address, int port) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated - used only for {@link co.elastic.apm.api.Span}
     */
    @Nonnull
    @Override
    @Deprecated
    public Transaction setDestinationService(@Nullable String resource) {
        throw new UnsupportedOperationException();
    }
}
