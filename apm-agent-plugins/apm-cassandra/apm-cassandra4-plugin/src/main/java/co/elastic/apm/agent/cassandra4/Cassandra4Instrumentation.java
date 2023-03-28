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
package co.elastic.apm.agent.cassandra4;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.cassandra.CassandraHelper;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Span;
import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.api.core.session.Request;
import com.datastax.oss.driver.api.core.session.Session;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletionStage;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class Cassandra4Instrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return ElementMatchers.nameContains("Session");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(ElementMatchers.isInterface()).and(hasSuperType(named("com.datastax.oss.driver.api.core.session.Session")));
    }

    /**
     * {@link Session#execute(com.datastax.oss.driver.api.core.session.Request, com.datastax.oss.driver.api.core.type.reflect.GenericType)}
     */
    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute")
            .and(returns(Object.class))
            .and(takesArgument(0, named("com.datastax.oss.driver.api.core.session.Request")))
            .and(takesArgument(1, named("com.datastax.oss.driver.api.core.type.reflect.GenericType")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("cassandra");
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.cassandra4.Cassandra4Instrumentation$Cassandra4Advice";
    }

    public static class Cassandra4Advice {
        private static final CassandraHelper cassandraHelper = new CassandraHelper(GlobalTracer.get());

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.This Session thiz,
                                     @Advice.Argument(0) Request request) {
            if (!(request instanceof Statement)) {
                return null;
            }

            // use statement keyspace (if any), then fallback to current session KS
            CqlIdentifier ks = request.getKeyspace();
            String keyspace = ks != null ? ks.toString() : null;
            if (ks == null) {
                keyspace = thiz.getKeyspace().map(CqlIdentifier::toString).orElse(null);
            }
            return cassandraHelper.startCassandraSpan(getQuery(request), request instanceof BoundStatement, keyspace);
        }

        @Nullable
        private static String getQuery(Request request) {
            if (request instanceof SimpleStatement) {
                return ((SimpleStatement) request).getQuery();
            } else if (request instanceof BoundStatement) {
                return ((BoundStatement) request).getPreparedStatement().getQuery();
            }
            return null;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Advice.Thrown @Nullable Throwable thrown,
                                  @Advice.Return Object result,
                                  @Nullable @Advice.Enter Object spanObj) {
            if (!(spanObj instanceof Span<?>)) {
                return;
            }
            Span<?> span = (Span<?>) spanObj;
            span.captureException(thrown).deactivate();
            if (result instanceof ResultSet) {
                setDestination(span, ((ResultSet) result).getExecutionInfo());
                span.end();
            } else if (result instanceof CompletionStage) {
                ((CompletionStage<?>) result).whenComplete((r, t) -> {
                    if (r instanceof AsyncResultSet) {
                        setDestination(span, ((AsyncResultSet) r).getExecutionInfo());
                    }
                    span.captureException(t).end();
                });
            } else {
                span.end();
            }
        }

        private static void setDestination(Span<?> span, ExecutionInfo info) {
            Node coordinator = info.getCoordinator();
            if (coordinator != null) {
                span.getContext().getDestination().withSocketAddress(coordinator.getEndPoint().resolve());
            }
        }
    }
}
