/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.jdbc;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.jdbc.helper.JdbcHelper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.jdbc.ConnectionInstrumentation.JDBC_INSTRUMENTATION_GROUP;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Creates spans for JDBC {@link Statement} execution
 */
public abstract class StatementInstrumentation extends ElasticApmInstrumentation {

    @SuppressWarnings("WeakerAccess")
    @Nullable
    @VisibleForAdvice
    public static HelperClassManager<JdbcHelper> jdbcHelperManager;

    private final ElementMatcher<? super MethodDescription> methodMatcher;

    StatementInstrumentation(ElasticApmTracer tracer, ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
        jdbcHelperManager = HelperClassManager.ForSingleClassLoader.of(tracer,
            "co.elastic.apm.agent.jdbc.helper.JdbcHelperImpl",
            "co.elastic.apm.agent.jdbc.helper.JdbcHelperImpl$1",
            "co.elastic.apm.agent.jdbc.helper.JdbcHelperImpl$ConnectionMetaData");
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        // DB2 driver does not call its Statements statements.
        return nameContains("Statement").or(nameStartsWith("com.ibm.db2.jcc"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named("java.sql.Statement")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton(JDBC_INSTRUMENTATION_GROUP);
    }

    /**
     * Instruments:
     * <ul>
     *     <li>{@link Statement#execute(String)} </li>
     *     <li>{@link Statement#execute(String, int[])} )} </li>
     *     <li>{@link Statement#execute(String, String[])} </li>
     *     <li>{@link Statement#execute(String, int)} </li>
     *     <li>{@link Statement#executeQuery(String)} </li>
     * </ul>
     */
    public static class ExecuteWithQueryInstrumentation extends StatementInstrumentation {

        public ExecuteWithQueryInstrumentation(ElasticApmTracer tracer) {
            super(tracer,
                named("execute").or(named("executeQuery"))
                    .and(takesArgument(0, String.class))
                    .and(isPublic())
            );
        }

        @Nullable
        @VisibleForAdvice
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static Span onBeforeExecute(@Advice.This Statement statement,
                                           @Advice.Argument(0) String sql) throws SQLException {
            if (tracer != null && jdbcHelperManager != null) {
                JdbcHelper helperImpl = jdbcHelperManager.getForClassLoaderOfClass(Statement.class);
                if (helperImpl != null) {
                    return helperImpl.createJdbcSpan(sql, statement.getConnection(), tracer.getActive(), false);
                }
            }
            return null;
        }

        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onAfterExecute(@Advice.This Statement statement,
                                          @Advice.Enter @Nullable Span span,
                                          @Advice.Thrown @Nullable Throwable t) throws SQLException {
            if (span != null) {
                if( t == null){
                    span.getContext()
                        .getDb()
                        .withAffectedRowsCount(statement.getUpdateCount());
                }
                span.captureException(t)
                    .deactivate()
                    .end();
            }
        }
    }

    /**
     * Instruments:
     * <ul>
     *     <li>{@link Statement#executeUpdate(String)} </li>
     *     <li>{@link Statement#executeUpdate(String, int[])} )} </li>
     *     <li>{@link Statement#executeUpdate(String, String[])} </li>
     *     <li>{@link Statement#executeUpdate(String, int)} </li>
     *     <li>{@link Statement#executeLargeUpdate(String)} (java8)</li>
     *     <li>{@link Statement#executeLargeUpdate(String, int[])} )} (java8)</li>
     *     <li>{@link Statement#executeLargeUpdate(String, String[])} (java8)</li>
     *     <li>{@link Statement#executeLargeUpdate(String, int)} (java8)</li>
     * </ul>
     */
    public static class ExecuteUpdateWithQueryInstrumentation extends StatementInstrumentation {

        public ExecuteUpdateWithQueryInstrumentation(ElasticApmTracer tracer) {
            super(tracer,
                named("executeUpdate").or(named("executeLargeUpdate"))
                    .and(takesArgument(0, String.class))
                    .and(isPublic())
            );
        }

        @Nullable
        @VisibleForAdvice
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static Span onBeforeExecute(@Advice.This Statement statement,
                                           @Advice.Argument(0) String sql) throws SQLException {
            if (tracer != null && jdbcHelperManager != null) {
                JdbcHelper helperImpl = jdbcHelperManager.getForClassLoaderOfClass(Statement.class);
                if (helperImpl != null) {
                    return helperImpl.createJdbcSpan(sql, statement.getConnection(), tracer.getActive(), false);
                }
            }
            return null;
        }

        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onAfterExecute(@Advice.Enter @Nullable Span span,
                                          @Advice.Thrown @Nullable Throwable t,
                                          @Advice.Return long returnValue /* bytebuddy converts int to long for us here ! */) {
            if (span != null) {
                if (t == null) {
                    span.getContext().getDb().withAffectedRowsCount(returnValue);
                }

                span.captureException(t)
                    .deactivate()
                    .end();
            }
        }
    }

    /**
     * Instruments {@link Statement#addBatch(String)}
     */
    public static class AddBatchInstrumentation extends StatementInstrumentation {

        public AddBatchInstrumentation(ElasticApmTracer tracer) {
            super(tracer,
                nameStartsWith("addBatch")
                    .and(takesArgument(0, String.class))
                    .and(isPublic())
            );
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void storeSql(@Advice.This Statement statement, @Advice.Argument(0) String sql) {
            if (jdbcHelperManager != null) {
                JdbcHelper helperImpl = jdbcHelperManager.getForClassLoaderOfClass(Statement.class);
                if (helperImpl != null) {
                    helperImpl.mapStatementToSql(statement, sql);
                }
            }
        }
    }

    // FIXME : what about .clearBatch() method ?

    /**
     * Instruments:
     *  <ul>
     *      <li>{@link Statement#executeBatch()} </li>
     *      <li>{@link Statement#executeLargeBatch()} (java8)</li>
     *  </ul>
     */
    public static class ExecuteBatchInstrumentation extends StatementInstrumentation {
        public ExecuteBatchInstrumentation(ElasticApmTracer tracer) {
            super(tracer,
                named("executeBatch").or(named("executeLargeBatch"))
                    .and(takesArguments(0))
                    .and(isPublic())
                // TODO : add check on method return type, otherwise seems to instrument hikari CP internal API
            );
        }

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static Span onBeforeExecute(@Advice.This Statement statement) throws SQLException {
            if (tracer != null && jdbcHelperManager != null) {
                JdbcHelper helperImpl = jdbcHelperManager.getForClassLoaderOfClass(Statement.class);
                if (helperImpl != null) {
                    final @Nullable String sql = helperImpl.retrieveSqlForStatement(statement);
                    return helperImpl.createJdbcSpan(sql, statement.getConnection(), tracer.getActive(), true);
                }
            }
            return null;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onAfterExecute(@Advice.Enter @Nullable Span span,
                                          @Advice.Thrown Throwable t,
                                          @Advice.Return Object returnValue) {
            if (span != null) {

                // for 'executeBatch' and 'executeLargeBatch', we have to compute the sum as Statement.getUpdateCount()
                // does not seem to return the sum of all elements. As we can use instanceof to check return type
                // we do not need to use a separate advice. 'execute' return value is auto-boxed into a Boolean,
                // but there is no extra allocation.
                long affectedCount = 0;
                if (returnValue instanceof int[]) {
                    int[] array = (int[]) returnValue;
                    for (int i = 0; i < array.length; i++) {
                        affectedCount += array[i];
                    }
                } else if (returnValue instanceof long[]) {
                    long[] array = (long[]) returnValue;
                    for (int i = 0; i < array.length; i++) {
                        affectedCount += array[i];
                    }
                }
                span.getContext()
                    .getDb()
                    .withAffectedRowsCount(affectedCount);

                span.captureException(t)
                    .deactivate()
                    .end();
            }
        }
    }

    /**
     * Instruments:
     *  <ul>
     *      <li>{@link PreparedStatement#executeUpdate()} </li>
     *      <li>{@link PreparedStatement#executeLargeUpdate()} ()} (java8)</li>
     *  </ul>
     */
    public static class ExecuteUpdateNoQueryInstrumentation extends StatementInstrumentation {
        public ExecuteUpdateNoQueryInstrumentation(ElasticApmTracer tracer) {
            super(tracer,
                named("executeUpdate").or(named("executeLargeUpdate"))
                    .and(takesArguments(0))
                    .and(isPublic())
            );
        }

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static Span onBeforeExecute(@Advice.This Statement statement) throws SQLException {
            if (tracer != null && jdbcHelperManager != null) {
                JdbcHelper helperImpl = jdbcHelperManager.getForClassLoaderOfClass(Statement.class);
                if (helperImpl != null) {
                    final @Nullable String sql = helperImpl.retrieveSqlForStatement(statement);
                    return helperImpl.createJdbcSpan(sql, statement.getConnection(), tracer.getActive(), true);
                }
            }
            return null;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onAfterExecute(@Advice.Enter @Nullable Span span,
                                          @Advice.Thrown @Nullable Throwable t,
                                          @Advice.Return long returnValue /* bytebuddy converts int to long for us here ! */) {
            if (span != null) {
                if (t == null) {
                    span.getContext().getDb().withAffectedRowsCount(returnValue);
                }

                span.captureException(t)
                    .deactivate()
                    .end();
            }
        }
    }

    /**
     * Instruments:
     * <ul>
     *     <li>{@link java.sql.PreparedStatement#execute}</li>
     *     <li>{@link java.sql.PreparedStatement#executeQuery}</li>
     * </ul>
     */
    public static class ExecutePreparedStatementInstrumentation extends StatementInstrumentation {
        public ExecutePreparedStatementInstrumentation(ElasticApmTracer tracer) {
            super(tracer,
                named("execute").or(named("executeQuery"))
                    .and(takesArguments(0))
                    .and(isPublic())
            );
        }

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static Span onBeforeExecute(@Advice.This Statement statement) throws SQLException {
            if (tracer != null && jdbcHelperManager != null) {
                JdbcHelper helperImpl = jdbcHelperManager.getForClassLoaderOfClass(Statement.class);
                if (helperImpl != null) {
                    final @Nullable String sql = helperImpl.retrieveSqlForStatement(statement);
                    return helperImpl.createJdbcSpan(sql, statement.getConnection(), tracer.getActive(), true);
                }
            }
            return null;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onAfterExecute(@Advice.This Statement statement,
                                          @Advice.Enter @Nullable Span span,
                                          @Advice.Thrown Throwable t) throws SQLException {
            if (span != null) {
                span.getContext()
                    .getDb()
                    // getUpdateCount javadoc indicates that this method should be called only once
                    // however in practice adding this extra call seem to not have noticeable side effects
                    .withAffectedRowsCount(statement.getUpdateCount());

                span.captureException(t)
                    .deactivate()
                    .end();
            }
        }
    }
}
