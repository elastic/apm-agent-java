package co.elastic.apm.api;

public interface Span extends AutoCloseable {

    /**
     * The name of the transaction.
     *
     * @param name the name of the span
     */
    void setName(String name);

    /**
     * The type of span.
     * <p>
     * The type is a hierarchical string used to group similar spans together.
     * For instance, all spans of MySQL queries are given the type db.mysql.query.
     * </p>
     * <p>
     * In the above example db is considered the type prefix. Though there are no naming restrictions for this prefix,
     * the following are standardized across all Elastic APM agents: app, db, cache, template, and ext.
     * </p>
     *
     * @param type the type of the span
     */
    void setType(String type);

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
