/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.cassandra3;

import co.elastic.apm.agent.cassandra.CassandraHelper;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Span;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.Statement;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.*;

public abstract class Cassandra3Instrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        // 2.x-3.x
        return CustomElementMatchers.classLoaderCanLoadClass("com.datastax.driver.core.BoundStatement");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("cassandra");
    }

    /**
     * Instruments {@code com.datastax.driver.core.SessionManager#executeAsync(Statement)}
     */
    public static class Async extends Cassandra3Instrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("com.datastax.driver.core.SessionManager");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("executeAsync")
                .and(returns(named("com.datastax.driver.core.ResultSetFuture")))
                .and(takesArguments(1))
                .and(takesArgument(0, named("com.datastax.driver.core.Statement")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.cassandra3.Cassandra3Instrumentation$Cassandra3Advice";
        }
    }

    /**
     * Instrument {@code AbstractSession#execute(Statement)}
     */
    public static class Sync extends Cassandra3Instrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("com.datastax.driver.core.AbstractSession");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("execute")
                .and(returns(named("com.datastax.driver.core.ResultSet")))
                .and(takesArguments(1))
                .and(takesArgument(0, named("com.datastax.driver.core.Statement")));
        }

        public static class AdviceClass {

            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void onEnter() {
                CassandraHelper.inSyncExecute(true);
            }

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
            public static void onExit(@Advice.Thrown @Nullable Throwable thrown, @Advice.Return @Nullable Object returnValue) {
                CassandraHelper.inSyncExecute(false);
            }
        }
    }

    @SuppressWarnings("unused")
    public static class Cassandra3Advice {

        private static final CassandraHelper cassandraHelper = new CassandraHelper(GlobalTracer.get());

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.This Session thiz,
                                     @Advice.Argument(0) Statement statement) {

            // use statement keyspace (if any), then fallback to current session KS
            String ks = statement.getKeyspace();
            if (ks == null) {
                ks = thiz.getLoggedKeyspace();
            }
            return cassandraHelper.startCassandraSpan(getQuery(statement), statement instanceof BoundStatement, ks);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Advice.Thrown @Nullable Throwable thrown,
                                  @Advice.Return ResultSetFuture result,
                                  @Advice.Enter @Nullable Object spanObj) {

            if (!(spanObj instanceof Span<?>)) {
                return;
            }
            final Span<?> span = (Span<?>) spanObj;
            span.captureException(thrown).deactivate();

            // synchronous or asynchronous depends on the calling method
            span.withSync(cassandraHelper.isSyncExecute());

            Futures.addCallback(((ResultSetFuture) result), new FutureCallback<ResultSet>() {
                @Override
                public void onSuccess(@Nullable ResultSet result) {
                    if (result != null) {
                        Host host = result.getExecutionInfo().getQueriedHost();
                        if (host != null) {
                            DestinationAddressSetter.Resolver.get().setDestination(span, host);
                        }
                    }
                    span.end();
                }

                @Override
                public void onFailure(Throwable t) {
                    span.captureException(t).end();
                }
            });
        }


        @Nullable
        private static String getQuery(Statement statement) {
            if (statement instanceof SimpleStatement) {
                return ((SimpleStatement) statement).getQueryString();
            } else if (statement instanceof BoundStatement) {
                return ((BoundStatement) statement).preparedStatement().getQueryString();
            }
            return null;
        }
    }

    private interface DestinationAddressSetter {
        void setDestination(Span<?> span, Host host);

        class Resolver {

            @Nullable
            private static volatile DestinationAddressSetter delegate;

            static DestinationAddressSetter get() {
                DestinationAddressSetter localDelegate = delegate;
                if (localDelegate == null) {
                    synchronized (Resolver.class) {
                        localDelegate = delegate;
                        if (localDelegate == null) {
                            try {
                                Class.forName("com.datastax.driver.core.Host").getMethod("getSocketAddress");
                                delegate = localDelegate = (DestinationAddressSetter) Class.forName(DestinationAddressSetter.class.getName() + "$WithSocketAddress").getEnumConstants()[0];
                            } catch (ReflectiveOperationException | LinkageError ignore) {
                                delegate = localDelegate = WithInetAddress.INSTANCE;
                            }
                        }
                    }
                }
                return localDelegate;
            }
        }

        /**
         * References the method {@link co.elastic.apm.agent.tracer.metadata.Destination#withSocketAddress(java.net.SocketAddress)} that has been introduced in 2.0.2
         * We must not reference this class directly to avoid it being loaded which may cause a linkage error.
         */
        enum WithSocketAddress implements DestinationAddressSetter {
            INSTANCE;

            @Override
            public void setDestination(Span<?> span, Host host) {
                span.getContext().getDestination().withSocketAddress(host.getSocketAddress());
            }
        }

        enum WithInetAddress implements DestinationAddressSetter {
            INSTANCE;

            @Override
            public void setDestination(Span<?> span, Host host) {
                span.getContext().getDestination().withInetAddress(host.getAddress());
            }
        }
    }
}
