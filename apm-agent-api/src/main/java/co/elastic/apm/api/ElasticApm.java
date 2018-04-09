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

/**
 * This class can be used to statically access the {@link ElasticApm}.
 * <p>
 * You can store the reference as an instance variable like so:
 * </p>
 * <pre>{@code
 * private static final ElasticApm elasticApm = ElasticApm.get();
 * }</pre>
 *
 * Then you can access the tracer to set a custom transaction name,
 * for example:
 * <pre>{@code
 * elasticApm.currentTransaction().setName("SuchController#muchMethod");
 * }</pre>
 */
public class ElasticApm implements Tracer {

    /**
     * The singleton instance of the Tracer.
     */
    private static final ElasticApm INSTANCE = new ElasticApm();

    private Tracer tracer = NoopTracer.INSTANCE;

    /**
     * {@link ElasticApm} may only be accessed via {@link #get()}
     */
    private ElasticApm() {
    }

    /**
     * Returns the tracer implementation.
     *
     * @return the tracer implementation (never {@code null})
     */
    @Nonnull
    public static ElasticApm get() {
        return INSTANCE;
    }

    /**
     * Statically registers the tracer implementation,
     * so that it can be accessed via {@link ElasticApm#get()}
     * <p>
     * This method is called by the actual {@link Tracer} implementation.
     * </p>
     * <p>
     * Users are not supposed to set a custom instrumentation so this method must not be {@code public}.
     * Otherwise it would be part of the public API.
     * </p>
     * <p>
     * Loading the concrete implementation from here is also not a good option,
     * as it could not be easily set to a different (possibly mocked) implementation in tests.
     * </p>
     * <p>
     * So the solution is to provide a package-private method which the implementation can call.
     * What's not so nice about this solution is that the implementation has to have a class in
     * the same package as the API in order to be able to call this package private method.
     * But as this is not a public method,
     * the concrete registration mechanism can always be changed later without breaking compatibility.
     * </p>
     *
     * @param tracer The tracer implementation to register.
     */
    void register(Tracer tracer) {
        INSTANCE.tracer = tracer;
    }

    // @VisibleForTesting
    void unregister() {
        INSTANCE.tracer = NoopTracer.INSTANCE;
    }

    @Override
    @Nonnull
    public Transaction startTransaction() {
        return tracer.startTransaction();
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
    @Override
    @Nonnull
    public Transaction currentTransaction() {
        Transaction transaction = tracer.currentTransaction();
        return transaction != null ? transaction : NoopTracer.NoopTransaction.INSTANCE;
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
    @Override
    @Nonnull
    public Span currentSpan() {
        Span span = tracer.currentSpan();
        return span != null ? span : NoopTracer.NoopSpan.INSTANCE;
    }

    @Override
    @Nonnull
    public Span startSpan() {
        return tracer.startSpan();
    }

    @Override
    public void captureException(@Nonnull Exception e) {
        tracer.captureException(e);
    }

    enum NoopTracer implements Tracer {

        INSTANCE;

        @Nonnull
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

        @Nonnull
        @Override
        public Span startSpan() {
            return NoopSpan.INSTANCE;
        }

        @Override
        public void captureException(@Nonnull Exception e) {
            // noop
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

        enum NoopSpan implements Span {
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
