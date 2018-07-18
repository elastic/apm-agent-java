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

/**
 * A transaction is the data captured by an agent representing an event occurring in a monitored service
 * and groups multiple spans in a logical group.
 * <p>
 * To get a reference to the current transaction, call {@link ElasticApm#currentTransaction()}.
 * </p>
 */
public interface Transaction {

    String TYPE_REQUEST = "request";

    /**
     * Override the name of the current transaction.
     * <p>
     * For supported frameworks,
     * the transaction name is determined automatically,
     * and can be overridden using this function.
     * </p>
     *
     * @param name A string describing name of the transaction.
     */
    void setName(String name);

    /**
     * The type of the transaction.
     * <p>
     * There’s a special type called {@link #TYPE_REQUEST request}
     * which is used by the agent for the transactions automatically created
     * when an incoming HTTP request is detected.
     * </p>
     *
     * @param type The type of the transaction.
     */
    void setType(String type);

    /**
     * A flat mapping of user-defined tags with string values.
     * <p>
     * Note: the tags are indexed in Elasticsearch so that they are searchable and aggregatable.
     * By all means,
     * you should avoid that user specified data,
     * like URL parameters,
     * is used as a tag key as it can lead to mapping explosions.
     * </p>
     *
     * @param key   The tag key.
     * @param value The tag value.
     */
    void addTag(String key, String value);

    /**
     * Call this to enrich collected performance data and errors with information about the user/client.
     * <p>
     * This method can be called at any point during the request/response life cycle (i.e. while a transaction is active).
     * The given context will be added to the active transaction.
     * </p>
     * <p>
     * If an error is captured, the context from the active transaction is used as context for the captured error.
     * </p>
     *
     * @param id       The user's id or {@code null}, if not applicable.
     * @param email    The user's email address or {@code null}, if not applicable.
     * @param username The user's name or {@code null}, if not applicable.
     */
    void setUser(String id, String email, String username);

    /**
     * End tracking the transaction.
     * <p>
     * Should be called e.g. at the end of a request or when ending a background task.
     * </p>
     */
    void end();
    
    /**
     * Creates span bound to the current transaction
     * @return {@link Span}
     */
    Span createSpan();

}
