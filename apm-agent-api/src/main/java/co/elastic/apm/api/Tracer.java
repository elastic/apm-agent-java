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
     * @param name A name for your transaction. Transactions are grouped by name.
     *             Can also be set later via {@link Transaction#setName(String)}
     * @param type There’s a special type called {@link Transaction#TYPE_REQUEST request} which is used by the agent for the transactions
     *             automatically created when an incoming HTTP request is detected.
     *             Can also be set later via {@link Transaction#setType(String)}
     * @return the started transaction
     */
    Transaction startTransaction(String name, String type);

    /**
     * Returns the currently active transaction
     *
     * @return the currently active transaction
     */
    Transaction currentTransaction();

}
