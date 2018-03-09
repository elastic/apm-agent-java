package co.elastic.apm.api;

/**
 * This class can be used to statically access the {@link Tracer}.
 * <p>
 * You can store the Tracer as an instance like so:
 * </p>
 * <pre>{@code
 * private static final Tracer tracer = ElasticApm.get();
 * }</pre>
 * <p>
 * Then you access the tracer to set a custom transaction name,
 * for example:
 * <pre>{@code
 * tracer.currentTransaction().setName("SuchController#muchMethod");
 * }</pre>
 * <p/>
 */
public class ElasticApm implements Tracer {

    /**
     * The singleton instance of the Tracer.
     */
    private static final ElasticApm INSTANCE = new ElasticApm();

    private static Tracer tracer = NoopTracer.INSTANCE;

    /**
     * {@link ElasticApm} may only be accessed via {@link #get()}
     */
    private ElasticApm() {
    }

    /**
     * Returns the tracer implementation.
     *
     * @return the tracer implementation (never <code>null</code>)
     */
    public static Tracer get() {
        return INSTANCE;
    }

    /**
     * Statically registers the tracer implementation,
     * so that it can be accessed via {@link ElasticApm#get()}
     * <p>
     * This method is called by the actual {@link Tracer} implementation.
     * </p>
     * <p>
     * Users are not supposed to set a custom instrumentation so this method must not be <code>public</code>.
     * Otherwise it would be part of the public API.
     * </p>
     * <p>
     * Loading the concrete implementation from here is also not a good option,
     * as it could not be easily set to a different (possibly mocked) implementation in tests.
     * </p>
     * <p>
     * So the solution is to provide a package-private method which the implementation can call.
     * What's not so nice about this solution is that the implementation has to have a class in
     * the same package as the api in order to be able to call this package private method.
     * But as this is not a public method,
     * the concrete registration mechanism can always be changed later without breaking compatibility.
     * </p>
     *
     * @param tracer The tracer implementation to register.
     */
    static void register(Tracer tracer) {
        synchronized (ElasticApm.class) {
            ElasticApm.tracer = tracer;
        }
    }

    // @VisibleForTesting
    static void unregister() {
        synchronized (ElasticApm.class) {
            ElasticApm.tracer = NoopTracer.INSTANCE;
        }
    }

    @Override
    public Transaction startTransaction() {
        return tracer.startTransaction();
    }

    /**
     * Returns the currently running transaction.
     * <p>
     * If there is no current transaction, this method will return a noop transaction.
     * </p>
     *
     * @return The currently running transaction, or a noop transaction (never <code>null</code>).
     */
    @Override
    public Transaction currentTransaction() {
        Transaction transaction = tracer.currentTransaction();
        return transaction != null ? transaction : NoopTracer.NoopTransaction.INSTANCE;
    }

    /**
     * Returns the currently running span.
     * <p>
     * If there is no current span, this method will return a noop span.
     * </p>
     *
     * @return The currently running span, or a noop span (never <code>null</code>).
     */
    @Override
    public Span currentSpan() {
        Span span = tracer.currentSpan();
        return span != null ? span : NoopTracer.NoopSpan.INSTANCE;
    }

    @Override
    public Span startSpan() {
        return tracer.startSpan();
    }

    enum NoopTracer implements Tracer {

        INSTANCE;

        @Override
        public Transaction startTransaction() {
            return NoopTransaction.INSTANCE;
        }

        @Override
        public Transaction currentTransaction() {
            return NoopTransaction.INSTANCE;
        }

        @Override
        public Span currentSpan() {
            return NoopSpan.INSTANCE;
        }

        @Override
        public co.elastic.apm.api.Span startSpan() {
            return NoopSpan.INSTANCE;
        }

        enum NoopTransaction implements Transaction {

            INSTANCE;

            @Override
            public void setName(String name) {
                // noop
            }

            @Override
            public void setType(String type) {
                // noop
            }

            @Override
            public void addTag(String key, String value) {
                // noop
            }

            @Override
            public void setUser(String id, String email, String username) {
                // noop
            }

            @Override
            public void end() {
                // noop
            }

            @Override
            public void close() {
                // noop
            }
        }

        enum NoopSpan implements co.elastic.apm.api.Span {
            INSTANCE;

            @Override
            public void setName(String name) {
                // noop
            }

            @Override
            public void setType(String type) {
                // noop
            }

            @Override
            public void end() {
                // noop
            }

            @Override
            public void close() {
                // noop
            }
        }
    }

}
