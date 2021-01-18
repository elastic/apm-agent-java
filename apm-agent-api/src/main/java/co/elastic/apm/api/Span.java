/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * For example, if a span is ended on another thread you must not add labels if there is a chance for a race between the {@link #end()}
 * and the {@link #addLabel(String, String)} method.
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
     * @deprecated use {@link #addLabel(String, String)} instead
     */
    @Nonnull
    @Deprecated
    Span addTag(String key, String value);

    /**
     * @param key   The label key.
     * @param value The label value.
     * @since 1.5.0
     * @deprecated use {@link #setLabel(String, String)}
     */
    @Nonnull
    @Deprecated
    Span addLabel(String key, String value);

    /**
     * @param key   The label key.
     * @param value The label value.
     * @since 1.5.0, APM Server 6.7
     * @deprecated use {@link #setLabel(String, Number)}
     */
    @Nonnull
    @Deprecated
    Span addLabel(String key, Number value);

    /**
     * @param key   The label key.
     * @param value The label value.
     * @since 1.5.0, APM Server 6.7
     * @deprecated use {@link #setLabel(String, boolean)}
     */
    @Nonnull
    @Deprecated
    Span addLabel(String key, boolean value);

    /**
     * <p>
     * Labels are used to add indexed information to transactions, spans, and errors.
     * Indexed means the data is searchable and aggregatable in Elasticsearch.
     * Multiple labels can be defined with different key-value pairs.
     * </p>
     * <ul>
     *     <li>Indexed: Yes</li>
     *     <li>Elasticsearch type: <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/object.html">object</a></li>
     *     <li>Elasticsearch field: {@code labels} (previously {@code context.tags} in stack version {@code < 7.0})</li>
     * </ul>
     * <p>
     * Label values can be a string, boolean, or number.
     * Because labels for a given key are stored in the same place in Elasticsearch, all label values of a given key must have the same data type.
     * Multiple data types per key will throw an exception, e.g. {@code {foo: bar}} and {@code {foo: 42}}
     * </p>
     * <p>
     * Important: Avoid defining too many user-specified labels.
     * Defining too many unique fields in an index is a condition that can lead to a
     * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping.html#mapping-limit-settings">mapping explosion</a>.
     * </p>
     *
     * @param key   The label key.
     * @param value The label value.
     * @since 1.19.0
     */
    @Nonnull
    Span setLabel(String key, String value);

    /**
     * <p>
     * Labels are used to add indexed information to transactions, spans, and errors.
     * Indexed means the data is searchable and aggregatable in Elasticsearch.
     * Multiple labels can be defined with different key-value pairs.
     * </p>
     * <ul>
     *     <li>Indexed: Yes</li>
     *     <li>Elasticsearch type: <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/object.html">object</a></li>
     *     <li>Elasticsearch field: {@code labels} (previously {@code context.tags} in stack version {@code < 7.0})</li>
     * </ul>
     * <p>
     * Label values can be a string, boolean, or number.
     * Because labels for a given key are stored in the same place in Elasticsearch, all label values of a given key must have the same data type.
     * Multiple data types per key will throw an exception, e.g. {@code {foo: bar}} and {@code {foo: 42}}
     * </p>
     * <p>
     * Note: Number and boolean labels were only introduced in APM Server 6.7+.
     * Using this API in combination with an older APM Server versions leads to validation errors.
     * </p>
     * <p>
     * Important: Avoid defining too many user-specified labels.
     * Defining too many unique fields in an index is a condition that can lead to a
     * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping.html#mapping-limit-settings">mapping explosion</a>.
     * </p>
     *
     * @param key   The label key.
     * @param value The label value.
     * @since 1.19.0, APM Server 6.7
     */
    @Nonnull
    Span setLabel(String key, Number value);

    /**
     * <p>
     * Labels are used to add indexed information to transactions, spans, and errors.
     * Indexed means the data is searchable and aggregatable in Elasticsearch.
     * Multiple labels can be defined with different key-value pairs.
     * </p>
     * <ul>
     *     <li>Indexed: Yes</li>
     *     <li>Elasticsearch type: <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/object.html">object</a></li>
     *     <li>Elasticsearch field: {@code labels} (previously {@code context.tags} in stack version {@code < 7.0})</li>
     * </ul>
     * <p>
     * Label values can be a string, boolean, or number.
     * Because labels for a given key are stored in the same place in Elasticsearch, all label values of a given key must have the same data type.
     * Multiple data types per key will throw an exception, e.g. {@code {foo: bar}} and {@code {foo: 42}}
     * </p>
     * <p>
     * Note: Number and boolean labels were only introduced in APM Server 6.7+.
     * Using this API in combination with an older APM Server versions leads to validation errors.
     * </p>
     * <p>
     * Important: Avoid defining too many user-specified labels.
     * Defining too many unique fields in an index is a condition that can lead to a
     * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping.html#mapping-limit-settings">mapping explosion</a>.
     * </p>
     *
     * @param key   The label key.
     * @param value The label value.
     * @since 1.19.0, APM Server 6.7
     */
    @Nonnull
    Span setLabel(String key, boolean value);

    /**
     * Sets the start timestamp of this event.
     *
     * @param epochMicros the timestamp of when this event happened, in microseconds (µs) since epoch
     * @return {@code this} for chaining
     * @since 1.5.0
     */
    Span setStartTimestamp(long epochMicros);

    /**
     * Sets the outcome of this event
     *
     * @param outcome {@link Outcome#SUCCESS} to indicate success, {@link Outcome#FAILURE} for failure, {
     * @return this
     * @link Outcome#UNKNOWN} to indicate unknown outcome
     */
    Span setOutcome(Outcome outcome);

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
     * describing queries are given the action `query`.
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
     * @param type    The general type of the new span
     * @param subtype The subtype of the new span
     * @param action  The action related to the new span
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
     * Ends the span and schedules it to be reported to the APM Server.
     * It is illegal to call any methods on a span instance which has already ended.
     * This also includes this method and {@link #startSpan()}.
     *
     * @param epochMicros the timestamp of when this event ended, in microseconds (µs) since epoch
     */
    void end(long epochMicros);

    /**
     * Captures an exception and reports it to the APM server.
     *
     * @param throwable the exception to report
     * @return the id of reported error
     */
    String captureException(Throwable throwable);

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
     * // Hook into a callback provided by the RPC framework that is called on outgoing requests
     * public Response onOutgoingRequest(Request request) throws Exception {
     *     // creates a span representing the external call
     *     Span span = ElasticApm.currentSpan()
     *             .startSpan("external", "http", null)
     *             .setName(request.getMethod() + " " + request.getHost());
     *     try (final Scope scope = transaction.activate()) {
     *         span.injectTraceHeaders((name, value) -&gt; request.addHeader(name, value));
     *         return request.execute();
     *     } catch (Exception e) {
     *         span.captureException(e);
     *         throw e;
     *     } finally {
     *         span.end();
     *     }
     * }
     * </pre>
     *
     * @param headerInjector tells the agent how to inject a header into the request object
     * @since 1.3.0
     */
    void injectTraceHeaders(HeaderInjector headerInjector);

}
