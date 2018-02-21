package co.elastic.apm.api;

public class ElasticApm implements Tracer {

    private static final ElasticApm INSTANCE = new ElasticApm();

    private static Tracer tracer = NoopTracer.INSTANCE;

    public static Tracer get() {
        return INSTANCE;
    }

    public static void register(Tracer tracer) {
        ElasticApm.tracer = tracer;
    }

    @Override
    public Transaction startTransaction() {
        return tracer.startTransaction();
    }

    @Override
    public Transaction currentTransaction() {
        return tracer.currentTransaction();
    }

    @Override
    public Span currentSpan() {
        return tracer.currentSpan();
    }

    @Override
    public Span startSpan() {
        return tracer.startSpan();
    }

    private enum NoopTracer implements Tracer {

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

        private enum NoopTransaction implements Transaction {

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

        private enum NoopSpan implements co.elastic.apm.api.Span {
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
