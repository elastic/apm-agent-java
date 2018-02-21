package co.elastic.apm.api;

public interface Tracer {

    /**
     * Use this function to create a custom transaction.
     * <p>
     * Note that the agent will do this for you automatically when ever your application receives an incoming HTTP request.
     * You only need to use this function to create custom transactions.
     * </p>
     * You can use the transaction in a try-with-resources block
     * <pre><code>
     * try (Transaction transaction = tracer.startTransaction()) {
     *     // do your thing...
     * }
     * </code></pre>
     *
     * @return the started transaction
     */
    Transaction startTransaction();

    /**
     * Returns the currently active transaction
     *
     * @return the currently active transaction
     */
    Transaction currentTransaction();

    Span currentSpan();

    Span startSpan();
}
