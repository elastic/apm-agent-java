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
import javax.annotation.Nullable;

/**
 * A span contains information about a specific code path, executed as part of a {@link Transaction}.
 * <p>
 * If for example a database query happens within a recorded transaction,
 * a span representing this database query may be created.
 * In such a case the name of the span will contain information about the query itself,
 * and the type will hold information about the database type.
 * </p>
 * <p>
 * Call {@link ElasticApm#currentSpan()} to get a reference of the current span.
 * </p>
 * <p>
 * Note: Calling any methods after {@link #end()} has been called is illegal.
 * You may only interact with spans when you have control over its lifecycle.
 * For example, if a span is ended on another thread you must not add tags if there is a chance for a race between the {@link #end()}
 * and the {@link #addTag(String, String)} method.
 * </p>
 */
public interface Span {

    /**
     * The name of the span.
     *
     * @param name the name of the span
     */
    @Nonnull
    Span setName(String name);

    /**
     * NOTE: THIS METHOD IS DEPRECATED AND WILL BE REMOVED IN VERSION 2.0.
     * Instead, setting the span type can be done when starting a new span through {@link #startSpan(String, String, String)}.
     * 
     * @param type the type of the span
     */
    @Nonnull
    @Deprecated
    Span setType(String type);

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
    @Nonnull
    Span addTag(String key, String value);

    /**
     * NOTE: THIS METHOD IS DEPRECATED AND WILL BE REMOVED IN VERSION 2.0.
     * Instead, start a new span through {@link #startSpan()} or {@link #startSpan(String, String, String)}.
     *
     * @return the started span, never {@code null}
     */
    @Nonnull
    @Deprecated
    Span createSpan();

    /**
     * Start and return a new typed custom span as a child of this span.
     * <p>
     * It is important to call {@link Span#end()} when the span has ended.
     * A best practice is to use the span in a try-catch-finally block.
     * Example:
     * </p>
     * <pre>
     * Span span = parent.startSpan("db", "mysql", "query");
     * try {
     *     span.setName("SELECT FROM customer");
     *     // do your thing...
     * } catch (Exception e) {
     *     span.captureException(e);
     *     throw e;
     * } finally {
     *     span.end();
     * }
     * </pre>
     * <p>
     * NOTE: Spans created via this method can not be retrieved by calling {@link ElasticApm#currentSpan()}.
     * See {@link #activate()} on how to achieve that.
     * </p>
     * <p>
     * The type, subtype and action strings are used to group similar spans together, with increasing resolution.
     * For instance, all DB spans are given the type `db`; all spans of MySQL queries are given the subtype `mysql` and all spans 
     * describing queries are give the action `query`.
     * </p>
     * <p>
     * In the above example `db` is considered the general type. Though there are no naming restrictions for the general types,
     * the following are standardized across all Elastic APM agents: `app`, `db`, `cache`, `template`, and `ext`.
     * </p>
     * <p>
     * NOTE: '.' (dot) character is not allowed within type, subtype and action. Any such character will be replaced with a '_'
     * (underscore) character.
     * </p>
     *
     * @param type      The general type of the new span
     * @param subtype   The subtype of the new span
     * @param action    The action related to the new span
     * @return the started span, never {@code null}
     */
    @Nonnull
    Span startSpan(String type, @Nullable String subtype, @Nullable String action);

    /**
     * Start and return a new custom span with no type, as a child of this span.
     * <p>
     * It is important to call {@link Span#end()} when the span has ended.
     * A best practice is to use the span in a try-catch-finally block.
     * Example:
     * </p>
     * <pre>
     * Span span = parent.startSpan();
     * try {
     *     span.setName("SELECT FROM customer");
     *     // do your thing...
     * } catch (Exception e) {
     *     span.captureException(e);
     *     throw e;
     * } finally {
     *     span.end();
     * }
     * </pre>
     * <p>
     * NOTE: Spans created via this method can not be retrieved by calling {@link ElasticApm#currentSpan()}.
     * See {@link #activate()} on how to achieve that.
     * </p>
     * 
     * @return the started span, never {@code null}
     */
    @Nonnull
    Span startSpan();

    /**
     * Ends the span and schedules it to be reported to the APM Server.
     * It is illegal to call any methods on a span instance which has already ended.
     * This also includes this method and {@link #startSpan()}.
     */
    void end();

    /**
     * Captures an exception and reports it to the APM server.
     *
     * @param throwable the exception to report
     */
    void captureException(Throwable throwable);

    /**
     * Returns the id of this span (never {@code null})
     * <p>
     * If this span represents a noop,
     * this method returns an empty string.
     * </p>
     *
     * @return the id of this span (never {@code null})
     */
    @Nonnull
    String getId();

    /**
     * Returns the id of this trace (never {@code null})
     * <p>
     * The trace-ID is consistent across all transactions and spans which belong to the same logical trace,
     * even for spans which happened in another service (given this service is also monitored by Elastic APM).
     * </p>
     * <p>
     * If this span represents a noop,
     * this method returns an empty string.
     * </p>
     *
     * @return the id of this span (never {@code null})
     */
    @Nonnull
    String getTraceId();

    /**
     * Makes this span the active span on the current thread until {@link Scope#close()} has been called.
     * <p>
     * Scopes should only be used in try-with-resource statements in order to make sure the {@link Scope#close()} method is called in all
     * circumstances.
     * Failing to close a scope can lead to memory leaks and corrupts the parent-child relationships.
     * </p>
     * <p>
     * This method should always be used within a try-with-resources statement:
     * </p>
     * <pre>
     * Span span = parent.startSpan();
     * // within the try block the span is available on the current thread via {@link ElasticApm#currentSpan()}
     * // this is also true for methods called within the try block
     * try (final Scope scope = span.activate()) {
     *     span.setName("SELECT FROM customer");
     *     span.setType("db.mysql.query");
     *     // do your thing...
     * } catch (Exception e) {
     *     span.captureException(e);
     *     throw e;
     * } finally {
     *     span.end();
     * }
     * </pre>
     * <p>
     * Note: {@link Span#activate()} and {@link Scope#close()} have to be called on the same thread.
     * </p>
     *
     * @return a scope which has to be {@link Scope#close()}d
     */
    Scope activate();

    /**
     * Returns true if this span is recorded and sent to the APM Server
     *
     * @return true if this span is recorded and sent to the APM Server
     */
    boolean isSampled();

    /**
     * Allows for manual propagation of the tracing headers.
     * <p>
     * If you want to manually instrument an RPC framework which is not already supported by the auto-instrumentation capabilities of the agent,
     * you can use this method to inject the required tracing headers into the header section of that framework's request object.
     * </p>
     * <p>
     * Example:
     * </p>
     * <pre>
     * span.injectTraceHeaders(request::addHeader);
     * </pre>
     *
     * @param headerInjector tells the agent how to inject a header into the request object
     * @since 1.3.0
     */
    void injectTraceHeaders(HeaderInjector headerInjector);

}
