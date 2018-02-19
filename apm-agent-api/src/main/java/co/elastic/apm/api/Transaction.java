package co.elastic.apm.api;

public interface Transaction extends AutoCloseable {

    String TYPE_REQUEST = "request";

    /**
     * The name of the transaction.
     * <p>
     * Can be used to set or overwrite the name of the transaction (visible in the performance monitoring breakdown).
     * If you don’t have access to the current transaction, you can also set the name using apm.setTransactionName().
     * </p>
     * <p>
     * Transactions with the same name and type are grouped together.
     * </p>
     *
     * @param name
     */
    void setName(String name);

    /**
     * The type of the transaction.
     * <p>
     * There’s a special type called request which is used by the agent for the transactions automatically created
     * when an incoming HTTP request is detected.
     * </p>
     *
     * @param type
     */
    void setType(String type);


    void addTag(String key, String value);

    /**
     * Call this to enrich collected performance data and errors with information about the user/client.
     * This function can be called at any point during the request/response life cycle (i.e. while a transaction is active).
     * <p>
     * The given context will be added to the active transaction.
     * </p>
     * <p>
     * It’s possible to call this function multiple times within the scope of the same active transaction.
     * For each call, the properties of the context argument are shallow merged with the context previously given.
     * </p>
     * <p>
     * If an error is captured, the context from the active transaction is used as context for the captured error,
     * and any custom context given as the 2nd argument to apm.captureError takes precedence and is shallow merged on top.
     * </p>
     * The provided user context is stored under context.user in Elasticsearch on both errors and transactions.
     *
     * @param id       the user's id or <code>null</code>, if not applicable
     * @param email    the user's email address or <code>null</code>, if not applicable
     * @param username the user's name or <code>null</code>, if not applicable
     */
    void setUser(String id, String email, String username);

    /**
     * Ends the transaction and truncates all un-ended child spans. If the transaction has already ended, nothing happens.
     */
    void end();

    /**
     * An alias for {@link #end()}
     */
    @Override
    void close();
}
