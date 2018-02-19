package co.elastic.apm.api;

public interface Span extends AutoCloseable {
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
     * Ends the transaction and truncates all un-ended child spans. If the transaction has already ended, nothing happens.
     */
    void end();

    /**
     * An alias for {@link #end()}
     */
    @Override
    void close();
}
