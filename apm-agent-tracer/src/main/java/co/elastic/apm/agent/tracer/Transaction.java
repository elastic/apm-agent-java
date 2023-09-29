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
package co.elastic.apm.agent.tracer;

import co.elastic.apm.agent.tracer.metadata.Faas;

import javax.annotation.Nullable;

public interface Transaction<T extends Transaction<T>> extends AbstractSpan<T> {

    String TYPE_REQUEST = "request";

    /**
     * Context
     * <p>
     * Any arbitrary contextual information regarding the event, captured by the agent, optionally provided by the user
     */
    @Override
    TransactionContext getContext();

    Faas getFaas();

    boolean isNoop();

    /**
     * The result of the transaction. HTTP status code for HTTP-related
     * transactions. This sets the result regardless of an already existing value.
     * should be used for user defined results
     */
    T withResult(@Nullable String result);

    /**
     * The result of the transaction. HTTP status code for HTTP-related
     * transactions. This sets the result only if it is not already set. should be
     * used for instrumentations
     */
    T withResultIfUnset(@Nullable String result);

    /**
     * Ignores this transaction, which makes it a noop so that it will not be reported to the APM Server.
     */
    void ignoreTransaction();

    void setFrameworkName(@Nullable String frameworkName);

    void setFrameworkVersion(@Nullable String frameworkVersion);
}
