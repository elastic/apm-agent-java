/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
 * This class can be used to statically access the {@link ElasticApm}.
 * <p>
 * You can store the reference as an instance variable like so:
 * </p>
 * <pre>{@code
 * private static final ElasticApm elasticApm = ElasticApm.get();
 * }</pre>
 * <p>
 * Then you can access the tracer to set a custom transaction name,
 * for example:
 * <pre>{@code
 * elasticApm.currentTransaction().setName("SuchController#muchMethod");
 * }</pre>
 */
public class ElasticApm {

    private ElasticApm() {
        // do not instantiate
    }

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
     * Returns the currently running transaction.
     * <p>
     * If there is no current transaction, this method will return a noop transaction,
     * which means that you never have to check for {@code null} values.
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
     * Returns the currently running span.
     * <p>
     * If there is no current span, this method will return a noop span,
     * which means that you never have to check for {@code null} values.
     * </p>
     *
     * @return The currently running span, or a noop span (never {@code null}).
     */
    @Nonnull
    public static Span currentSpan() {
        Object span = doGetCurrentSpan();
        return span != null ? new SpanImpl(span) : NoopSpan.INSTANCE;
    }

    private static Object doGetCurrentSpan() {
        // co.elastic.apm.api.ElasticApmInstrumentation.CurrentSpanInstrumentation.doGetCurrentSpan
        return null;
    }

    /**
     * Start and return a new custom span associated with the current active transaction.
     * <p>
     * It is important to call {@link Span#close()} when the span has ended.
     * A best practice is to use the span in a try-with-resources block.
     * Example:
     * </p>
     * <pre>
     * try (Span span = tracer.startSpan()) {
     *     span.setName("SELECT FROM customer");
     *     span.setType("db.mysql.query");
     *     // do your thing...
     * }
     * </pre>
     *
     * @return the started span, or {@code null} if there is no current transaction
     */
    @Nonnull
    public static Span startSpan() {
        Object span = doStartSpan();
        return span != null ? new SpanImpl(span) : NoopSpan.INSTANCE;
    }

    private static Object doStartSpan() {
        // co.elastic.apm.api.ElasticApmInstrumentation.StartSpanInstrumentation.doStartSpan
        return null;
    }

    /**
     * Captures an exception and reports it to the APM server.
     *
     * @param e the exception to record
     */
    public static void captureException(@Nullable Exception e) {
        // co.elastic.apm.api.ElasticApmInstrumentation.CaptureExceptionInstrumentation.captureException
    }

}
