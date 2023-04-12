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
import java.lang.invoke.MethodHandle;

/**
 * This class is the main entry point of the public API for the Elastic APM agent.
 * <p>
 * The tracer gives you access to the currently active transaction and span.
 * It can also be used to track an exception.
 * To use the API, you can just invoke the static methods on this class.
 * </p>
 * Use this API to set a custom transaction name,
 * for example:
 * <pre>{@code
 * ElasticApm.currentTransaction().setName("SuchController#muchMethod");
 * }</pre>
 */
public class ElasticApm {

    ElasticApm() {
        // do not instantiate
    }

    /**
     * Use this method to create a custom transaction.
     * <p>
     * Note that the agent will do this for you automatically when ever your application receives an incoming HTTP request.
     * You only need to use this method to create custom transactions.
     * </p>
     * <p>
     * It is important to call {@link Transaction#end()} when the transaction has ended.
     * A best practice is to use the transaction in a try-catch-finally block.
     * Example:
     * </p>
     * <pre>
     * Transaction transaction = ElasticApm.startTransaction();
     * try {
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
     * Note: Transactions created via this method can not be retrieved by calling {@link #currentSpan()} or {@link #currentTransaction()}.
     * See {@link Transaction#activate()} on how to achieve that.
     * </p>
     *
     * @return the started transaction.
     */
    @Nonnull
    public static Transaction startTransaction() {
        Object transaction = doStartTransaction();
        return transaction != null ? new TransactionImpl(transaction) : NoopTransaction.INSTANCE;
    }

    private static Object doStartTransaction() {
        // co.elastic.apm.api.ElasticApmInstrumentation.StartTransactionInstrumentation.doStartTransaction
        return null;
    }

    /**
     * Similar to {@link ElasticApm#startTransaction()} but creates this transaction as the child of a remote parent.
     *
     * <p>
     * Example:
     * </p>
     * <pre>
     * // Hook into a callback provided by the framework that is called on incoming requests
     * public Response onIncomingRequest(Request request) throws Exception {
     *     // creates a transaction representing the server-side handling of the request
     *     Transaction transaction = ElasticApm.startTransactionWithRemoteParent(key -&gt; request.getHeader(key));
     *     try (final Scope scope = transaction.activate()) {
     *         String name = "a useful name like ClassName#methodName where the request is handled";
     *         transaction.setName(name);
     *         transaction.setType(Transaction.TYPE_REQUEST);
     *         return request.handle();
     *     } catch (Exception e) {
     *         transaction.captureException(e);
     *         throw e;
     *     } finally {
     *         transaction.end();
     *     }
     * }
     * </pre>
     * <p>
     * Note: If the protocol supports multi-value headers, use {@link #startTransactionWithRemoteParent(HeaderExtractor, HeadersExtractor)}
     * </p>
     *
     * @param headerExtractor a function which receives a header name and returns the fist header with that name
     * @return the started transaction
     * @since 1.3.0
     */
    @Nonnull
    public static Transaction startTransactionWithRemoteParent(final HeaderExtractor headerExtractor) {
        return startTransactionWithRemoteParent(headerExtractor, null);
    }

    /**
     * Similar to {@link ElasticApm#startTransaction()} but creates this transaction as the child of a remote parent.
     *
     * <p>
     * Example:
     * </p>
     * <pre>
     * // Hook into a callback provided by the framework that is called on incoming requests
     * public Response onIncomingRequest(Request request) throws Exception {
     *     // creates a transaction representing the server-side handling of the request
     *     Transaction transaction = ElasticApm.startTransactionWithRemoteParent(request::getHeader, request::getHeaders);
     *     try (final Scope scope = transaction.activate()) {
     *         String name = "a useful name like ClassName#methodName where the request is handled";
     *         transaction.setName(name);
     *         transaction.setType(Transaction.TYPE_REQUEST);
     *         return request.handle();
     *     } catch (Exception e) {
     *         transaction.captureException(e);
     *         throw e;
     *     } finally {
     *         transaction.end();
     *     }
     * }
     * </pre>
     * <p>
     * Note: If the protocol does not support multi-value headers, use {@link #startTransactionWithRemoteParent(HeaderExtractor)}
     * </p>
     * <p>
     *
     * @param headerExtractor a function which receives a header name and returns the fist header with that name
     * @param headersExtractor  a function which receives a header name and returns all headers with that name
     * @return the started transaction
     * @since 1.3.0
     */
    @Nonnull
    public static Transaction startTransactionWithRemoteParent(HeaderExtractor headerExtractor, HeadersExtractor headersExtractor) {
        Object transaction = doStartTransactionWithRemoteParentFunction(ApiMethodHandles.GET_FIRST_HEADER, headerExtractor,
            ApiMethodHandles.GET_ALL_HEADERS, headersExtractor);
        return transaction != null ? new TransactionImpl(transaction) : NoopTransaction.INSTANCE;
    }

    private static Object doStartTransactionWithRemoteParentFunction(MethodHandle getFirstHeader, HeaderExtractor headerExtractor,
                                                                     MethodHandle getAllHeaders, HeadersExtractor headersExtractor) {
        // co.elastic.apm.agent.pluginapi.ElasticApmApiInstrumentation.StartTransactionWithRemoteParentInstrumentation
        return null;
    }

    /**
     * Returns the currently running transaction.
     * <p>
     * If there is no current transaction, this method will return a noop transaction,
     * which means that you never have to check for {@code null} values.
     * </p>
     * <p>
     * NOTE: Transactions created via {@link #startTransaction()} can not be retrieved by calling this method.
     * See {@link Transaction#activate()} on how to achieve that.
     * </p>
     *
     * @return The currently running transaction, or a noop transaction (never {@code null}).
     */
    @Nonnull
    public static Transaction currentTransaction() {
        Object transaction = doGetCurrentTransaction();
        return transaction != null ? new TransactionImpl(transaction) : NoopTransaction.INSTANCE;
    }

    private static Object doGetCurrentTransaction() {
        // co.elastic.apm.api.ElasticApmInstrumentation.CurrentTransactionInstrumentation.doGetCurrentTransaction
        return null;
    }

    /**
     * Returns the currently active span or transaction.
     * <p>
     * If there is no current span, this method will return a noop span,
     * which means that you never have to check for {@code null} values.
     * </p>
     * <p>
     * Note that even if this method is returning a noop span,
     * you can still {@link Span#captureException(Throwable) capture exceptions} on it.
     * These exceptions will not have a link to a Span or a Transaction.
     * </p>
     * <p>
     * NOTE: Transactions created via {@link Span#startSpan()} or via {@link Span#startSpan(String, String, String)} can not be retrieved
     * by calling this method.
     * See {@link Span#activate()} on how to achieve that.
     * </p>
     *
     * @return The currently active span, or transaction, or a noop span (never {@code null}).
     */
    @Nonnull
    public static Span currentSpan() {
        Object span = doGetCurrentSpan();
        return span != null ? new SpanImpl(span) : NoopSpan.INSTANCE;
    }

    private static Object doGetCurrentSpan() {
        // co.elastic.apm.api.ElasticApmApiInstrumentation.CurrentSpanInstrumentation.doGetCurrentSpan
        return null;
    }

    /**
     * Captures an exception and reports it to the APM server.
     *
     * @param e the exception to record
     * @deprecated use {@link #currentSpan()}.{@link Span#captureException(Throwable) captureException(Throwable)} instead
     */
    @Deprecated
    public static void captureException(@Nullable Throwable e) {
        // co.elastic.apm.api.ElasticApmInstrumentation.CaptureExceptionInstrumentation.captureException
    }

    /**
     * Associates a class loader with a service name and version.
     * <p>
     * The association is used to overwrite the autodetected service name and version when a transaction is started.
     * </p>
     * <p>
     * NOTE: If the class loader already is associated with a service name and version,
     * the existing information will not be overwritten.
     * </p>
     *
     * @param classLoader the class loader which should be associated with the given service name and version
     * @param serviceName the service name
     * @param serviceVersion the service version
     */
    public static void setServiceInfoForClassLoader(@Nullable ClassLoader classLoader, @Nullable String serviceName, @Nullable String serviceVersion) {
        // co.elastic.apm.api.ElasticApmInstrumentation.SetServiceInfoForClassLoader.setServiceInfoForClassLoader
    }

    /**
     * Returns the current value of a config option (regardless of however and whenever that option was (re)set)
     * <p>
     * This applies to dynamically updated values too, so for example "transaction_sample_rate"
     * can be dynamically reset (eg by executing
     *   System.setProperty("elastic.apm.transaction_sample_rate", "0.6")
     * or changing the "transaction_sample_rate" in the property file or from fleet UI)
     * and calling getConfig("transaction_sample_rate") would provide the current value
     * </p>
     * <p>
     * If an invalid key is passed as the first argument, or if the incorrect type for
     * the key is passed as the second argument, the method will throw an IllegalArgumentException
     * </p>
     * <p>
     * Note the value returned can be null if the "public-api" set of instrumentations
     * has not been applied and also if the agent hasn't finished initializing
     * </p>
     *
     * @param key the string option
     * @param type the type of the option (String, Long, Integer, etc)
     * @return The current value for the option, per the option type (String, Long, Integer, etc)
     * @since 1.37.0
     */
    public static <T> T getConfig(String key, Class<T> type) {
        Object value = doGetConfig(key);
        if (value == null) {
            throw new IllegalArgumentException("There is no such option: " + key);
        } else if (value instanceof IllegalStateException) {
            return null;
        } else if (type.isInstance(value)) {
            return (T) value;
        } else {
            throw new IllegalArgumentException("The option: '"+key+"' is not of type "+type.getName());
        }
    }

    private static Object doGetConfig(String key) {
        // co.elastic.apm.api.ElasticApmInstrumentation.ConfigInstrumentation.doGetConfig
        return null;
    }

    /**
     * Convenience method that just calls System.setProperty("elastic.apm."+key, value);
     * <p>
     * Note there is a delay between executing the update and it taking effect. The corresponding
     * {@link #getConfig(String, Class)} ()} will only report the new value after it has taken effect.
     * Note also that only dynamic options can be updated, other updates will simply be ignored.
     * </p>
     *
     * @param key the string option
     * @param value the value for that option. No validation is applied here, so invalid
     *              values for an option will be ignored by the configuration system
     * @since 1.37.0
     */
    public static void setConfig(String key, String value) {
        System.setProperty("elastic.apm."+key, value);
    }
}
