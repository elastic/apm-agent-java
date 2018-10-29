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
     * Start and return a new custom span as a child of this transaction.
     * <p>
     * It is important to call {@link Span#end()} when the span has ended.
     * A best practice is to use the span in a try-catch-finally block.
     * Example:
     * </p>
     * <pre>
     * Span span = transaction.startSpan();
     * try {
     *     span.setName("SELECT FROM customer");
     *     span.setType("db.mysql.query");
     *     // do your thing...
     * } catch (Exception e) {
     *     ElasticApm.captureException(e);
     *     throw e;
     * } finally {
     *     span.end();
     * }
     * </pre>
     *
     * @return the started span, never {@code null}
     */
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
     * Makes this transaction the active transaction on the current thread until {@link Scope#close()} has been called.
     * <p>
     * Scopes should only be used in try-with-resource statements in order to make sure the {@link Scope#close()} method is called in all
     * circumstances.
     * Failing to close a scope can lead to memory leaks and corrupts the parent-child relationships.
     * </p>
     * <p>
     * This method should always be used within a try-with-resources statement:
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
     * </p>
     * <p>
     * Note: {@link Transaction#activate()} and {@link Scope#close()} have to be called on the same thread.
     * </p>
     *
     * @return a scope which has to be {@link Scope#close()}d
     */
    @Override
    Scope activate();
}
