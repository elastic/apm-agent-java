package co.elastic.apm.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The tracer gives you access to the currently active transaction and span.
 * It can also be used to track an exception.
 */
public interface Tracer {

    /**
     * Use this method to create a custom transaction.
     * <p>
     * Note that the agent will do this for you automatically when ever your application receives an incoming HTTP request.
     * You only need to use this method to create custom transactions.
     * </p>
     * <p>
     * It is important to call {@link Transaction#end()} when the transaction has ended.
     * A best practice is to use the transaction in a try-with-resources block.
     * Example:
     * </p>
     * <pre>
     * try (Transaction transaction = tracer.startTransaction()) {
     *     transaction.setName("MyController#myAction");
     *     span.setType(Transaction.TYPE_REQUEST);
     *     // do your thing...
     * }
     * </pre>
     *
     * @return the started transaction
     */
    @Nonnull
    Transaction startTransaction();

    /**
     * Returns the currently active transaction
     *
     * @return the currently active transaction
     */
    @Nullable
    Transaction currentTransaction();

    /**
     * Returns the currently active span
     *
     * @return The currently active span.
     */
    @Nullable
    Span currentSpan();

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
     */
    @Nonnull
    Span startSpan();

    /**
     * Captures an exception and reports it to the APM server.
     */
    void captureException(@Nonnull Exception e);
}
