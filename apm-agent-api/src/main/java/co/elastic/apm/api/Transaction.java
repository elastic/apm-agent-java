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

/**
 * A transaction is the data captured by an agent representing an event occurring in a monitored service
 * and groups multiple spans in a logical group.
 * <p>
 * To get a reference to the current transaction, call {@link ElasticApm#currentTransaction()}.
 * </p>
 */
public interface Transaction extends Span {

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
    @Nonnull
    Transaction setName(String name);

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
    @Nonnull
    Transaction setType(String type);

    /**
     * Override the name of the framework for the current transaction.
     * <p>
     * For supported frameworks,
     * the framework name is determined automatically,
     * and can be overridden using this function.
     * </p>
     * <p>
     * <code>null</code> or the empty string will make the agent omit this field.
     * </p>
     *
     * @param frameworkName The name of the framework
     */
    @Nonnull
    Transaction setFrameworkName(String frameworkName);

    /**
     * {@inheritDoc}
     *
     * @deprecated use {@link #addLabel(String, String)} instead
     */
    @Nonnull
    @Deprecated
    Transaction addTag(String key, String value);

    /**
     * {@inheritDoc}
     *
     * @deprecated use {@link #setLabel(String, String)} instead
     */
    @Nonnull
    @Deprecated
    @Override
    Transaction addLabel(String key, String value);

    /**
     * {@inheritDoc}
     *
     * @deprecated use {@link #setLabel(String, Number)} instead
     */
    @Nonnull
    @Deprecated
    @Override
    Transaction addLabel(String key, Number value);

    /**
     * {@inheritDoc}
     *
     * @deprecated use {@link #setLabel(String, boolean)} instead
     */
    @Nonnull
    @Deprecated
    @Override
    Transaction addLabel(String key, boolean value);

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    Transaction setLabel(String key, String value);

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    Transaction setLabel(String key, Number value);

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    Transaction setLabel(String key, boolean value);

    /**
     * Custom context is used to add non-indexed,
     * custom contextual information to transactions.
     * Non-indexed means the data is not searchable or aggregatable in Elasticsearch,
     * and you cannot build dashboards on top of the data.
     * However, non-indexed information is useful for other reasons,
     * like providing contextual information to help you quickly debug performance issues or errors.
     *
     * @param key   The custom context key.
     * @param value The custom context value.
     */
    @Nonnull
    Transaction addCustomContext(String key, String value);

    /**
     * Custom context is used to add non-indexed,
     * custom contextual information to transactions.
     * Non-indexed means the data is not searchable or aggregatable in Elasticsearch,
     * and you cannot build dashboards on top of the data.
     * However, non-indexed information is useful for other reasons,
     * like providing contextual information to help you quickly debug performance issues or errors.
     *
     * @param key   The custom context key.
     * @param value The custom context value.
     */
    @Nonnull
    Transaction addCustomContext(String key, Number value);

    /**
     * Custom context is used to add non-indexed,
     * custom contextual information to transactions.
     * Non-indexed means the data is not searchable or aggregatable in Elasticsearch,
     * and you cannot build dashboards on top of the data.
     * However, non-indexed information is useful for other reasons,
     * like providing contextual information to help you quickly debug performance issues or errors.
     *
     * @param key   The custom context key.
     * @param value The custom context value.
     */
    @Nonnull
    Transaction addCustomContext(String key, boolean value);

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
    Transaction setUser(String id, String email, String username);

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
     * @param domain   The user's domain or {@code null}, if not applicable.
     */
    Transaction setUser(String id, String email, String username, String domain);

    /**
     * A string describing the result of the transaction.
     * This is typically the HTTP status code, or e.g. "success" for a background task
     *
     * @param result a string describing the result of the transaction
     */
    Transaction setResult(String result);

    @Override
    Transaction setStartTimestamp(long epochMicros);

    /**
     * Sets the transaction outcome
     *
     * @param outcome {@link Outcome#SUCCESS} to indicate success, {@link Outcome#FAILURE} for failure,
     *                {@link Outcome#UNKNOWN} to indicate unknown outcome
     * @return this
     */

    Transaction setOutcome(Outcome outcome);

    /**
     * End tracking the transaction.
     * <p>
     * Should be called e.g. at the end of a request or when ending a background task.
     * </p>
     */
    void end();

    /**
     * NOTE: THIS METHOD IS DEPRECATED AND WILL BE REMOVED IN VERSION 2.0.
     * Instead, start a new span through {@link Span#startSpan()} or {@link Span#startSpan(String, String, String)}.
     *
     * @return the started span, never {@code null}
     */
    @Nonnull
    @Deprecated
    Span createSpan();

    /**
     * Returns the id of this transaction (never {@code null})
     * <p>
     * If this transaction represents a noop,
     * this method returns an empty string.
     * </p>
     *
     * @return the id of this transaction (never {@code null})
     */
    @Nonnull
    String getId();

    /**
     * <p>
     * If the transaction does not have a parent-ID yet,
     * calling this method generates a new ID,
     * sets it as the parent-ID of this transaction,
     * and returns it as a `String`.
     * </p>
     * <p>
     * This enables the correlation of the spans the JavaScript Real User Monitoring (RUM) agent creates for the initial page load
     * with the transaction of the backend service.
     * If your backend service generates the HTML page dynamically,
     * initializing the JavaScript RUM agent with the value of this method allows analyzing the time spent in the browser vs in the backend services.
     * </p>
     * <p>
     * To enable the JavaScript RUM agent when using an HTML templating language like Freemarker,
     * add {@code ElasticApm.currentTransaction()} with the key {@code "transaction"} to the model.
     * </p>
     * <p>
     * Also, add a snippet similar to this to the body of your HTML pages,
     * preferably before other JS libraries:
     * </p>
     *
     * <pre>{@code
     * <script src="elastic-apm-js-base/dist/bundles/elastic-apm-js-base.umd.min.js"></script>
     * <script>
     *   elasticApm.init({
     *     serviceName: "service-name",
     *     serverUrl: "http://localhost:8200",
     *     pageLoadTraceId: "${transaction.traceId}",
     *     pageLoadSpanId: "${transaction.ensureParentId()}",
     *     pageLoadSampled: ${transaction.sampled}
     *   })
     * </script>
     * }</pre>
     *
     * <p>
     * See the JavaScript RUM agent documentation for more information.
     * </p>
     *
     * @return the parent-ID for this transaction. Updates the transaction to use a new parent-ID if it has previously been unset.
     */
    @Nonnull
    String ensureParentId();

    /**
     * Makes this transaction the active transaction on the current thread until {@link Scope#close()} has been called.
     * <p>
     * Scopes should only be used in try-with-resource statements in order to make sure the {@link Scope#close()} method is called in all
     * circumstances.
     * Failing to close a scope can lead to memory leaks and corrupts the parent-child relationships.
     * </p>
     * <p>
     * This method should always be used within a try-with-resources statement:
     * </p>
     * <pre>
     * Transaction transaction = ElasticApm.startTransaction();
     * // within the try block the transaction is available on the current thread via {@link ElasticApm#currentTransaction()}
     * // this is also true for methods called within the try block
     * try (final Scope scope = transaction.activate()) {
     *     transaction.setName("MyController#myAction");
     *     transaction.setType(Transaction.TYPE_REQUEST);
     *     // do your thing...
     * } catch (Exception e) {
     *     transaction.captureException(e);
     *     throw e;
     * } finally {
     *     transaction.end();
     * }
     * </pre>
     * <p>
     * Note: {@link Transaction#activate()} and {@link Scope#close()} have to be called on the same thread.
     * </p>
     *
     * @return a scope which has to be {@link Scope#close()}d
     */
    @Override
    Scope activate();

}
