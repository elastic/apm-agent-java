/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.bootstrap.MethodHandleDispatcher;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.Statement;

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
public abstract class StatementInstrumentation extends JdbcInstrumentation {

    private final ElementMatcher<? super MethodDescription> methodMatcher;

    StatementInstrumentation(ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
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
    @SuppressWarnings("DuplicatedCode")
    public static class ExecuteWithQueryInstrumentation extends StatementInstrumentation {

        public ExecuteWithQueryInstrumentation() {
            super(
                named("execute").or(named("executeQuery"))
                    .and(takesArgument(0, String.class))
                    .and(isPublic())
            );
        }

        @Nullable
        @VisibleForAdvice
        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static Span onBeforeExecute(@Advice.Origin Class<?> clazz,
                                            @Advice.This Statement statement,
                                            @Advice.Argument(0) String sql) throws Throwable {

            if (MethodHandleDispatcher.USE_REFLECTION) {
                return (Span) MethodHandleDispatcher
                    .getMethod(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#createJdbcSpan")
                    .invoke(null, sql, statement, false);
            } else {
                return (Span) MethodHandleDispatcher
                    .getMethodHandle(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#createJdbcSpan")
                    .invoke(sql, statement, false);
            }
        }


        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onAfterExecute(@Advice.Origin Class<?> clazz,
                                           @Advice.This Statement statement,
                                           @Advice.Enter @Nullable Span span,
                                           @Advice.Thrown @Nullable Throwable t) throws Throwable {
            if (MethodHandleDispatcher.USE_REFLECTION) {
                MethodHandleDispatcher
                    .getMethod(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#onAfterExecuteQuery")
                    .invoke(null, statement, span, t);
            } else {
                MethodHandleDispatcher
                    .getMethodHandle(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#onAfterExecuteQuery")
                    .invoke(statement, span, t);
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

        public ExecuteUpdateWithQueryInstrumentation() {
            super(
                named("executeUpdate").or(named("executeLargeUpdate"))
                    .and(takesArgument(0, String.class))
                    .and(isPublic())
            );
        }

        @Nullable
        @VisibleForAdvice
        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static Span onBeforeExecute(@Advice.Origin Class<?> clazz,
                                            @Advice.This Statement statement,
                                            @Advice.Argument(0) String sql) throws Throwable {
            if (MethodHandleDispatcher.USE_REFLECTION) {
                return (Span) MethodHandleDispatcher
                    .getMethod(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#createJdbcSpan")
                    .invoke(null, sql, statement, false);
            } else {
                return (Span) MethodHandleDispatcher
                    .getMethodHandle(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#createJdbcSpan")
                    .invoke(sql, statement, false);
            }
        }

        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onAfterExecute(@Advice.Origin Class<?> clazz,
                                           @Advice.Enter @Nullable Span span,
                                           @Advice.Thrown @Nullable Throwable t,
                                           @Advice.Return long returnValue /* bytebuddy converts int to long for us here ! */) throws Throwable {
            if (MethodHandleDispatcher.USE_REFLECTION) {
                MethodHandleDispatcher
                    .getMethod(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#onAfter")
                    .invoke(null, span, t, returnValue);
            } else {
                MethodHandleDispatcher
                    .getMethodHandle(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#onAfter")
                    .invoke(span, t, returnValue);
            }
        }

    }

    /**
     * Instruments {@link Statement#addBatch(String)}
     */
    public static class AddBatchInstrumentation extends StatementInstrumentation {

        public AddBatchInstrumentation() {
            super(
                nameStartsWith("addBatch")
                    .and(takesArgument(0, String.class))
                    .and(isPublic())
            );
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void storeSql(@Advice.Origin Class<?> clazz, @Advice.This Statement statement, @Advice.Argument(0) String sql) throws Throwable {
            if (MethodHandleDispatcher.USE_REFLECTION) {
                MethodHandleDispatcher
                    .getMethod(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#mapStatementToSql")
                    .invoke(null, statement, sql);
            } else {
                MethodHandleDispatcher
                    .getMethodHandle(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#mapStatementToSql")
                    .invoke(statement, sql);
            }
        }
    }

    // FIXME : what about .clearBatch() method ?

    /**
     * Instruments:
     * <ul>
     *     <li>{@link Statement#executeBatch()} </li>
     *     <li>{@link Statement#executeLargeBatch()} (java8)</li>
     * </ul>
     */
    public static class ExecuteBatchInstrumentation extends StatementInstrumentation {
        public ExecuteBatchInstrumentation() {
            super(
                named("executeBatch").or(named("executeLargeBatch"))
                    .and(takesArguments(0))
                    .and(isPublic())
                // TODO : add check on method return type, otherwise seems to instrument hikari CP internal API
            );
        }

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static Span onBeforeExecute(@Advice.Origin Class<?> clazz, @Advice.This Statement statement) throws Throwable {
            if (MethodHandleDispatcher.USE_REFLECTION) {
                return (Span) MethodHandleDispatcher
                    .getMethod(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#createJdbcSpanLookupSql")
                    .invoke(null, statement, false);
            } else {
                return (Span) MethodHandleDispatcher
                    .getMethodHandle(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#createJdbcSpanLookupSql")
                    .invoke(statement, false);
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onAfterExecute(@Advice.Origin Class<?> clazz,
                                           @Advice.Enter @Nullable Span span,
                                           @Advice.Thrown Throwable t,
                                           @Advice.Return Object returnValue) throws Throwable {
            if (MethodHandleDispatcher.USE_REFLECTION) {
                MethodHandleDispatcher
                    .getMethod(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#onAfterExecuteBatch")
                    .invoke(null, span, t, returnValue);
            } else {
                MethodHandleDispatcher
                    .getMethodHandle(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#onAfterExecuteBatch")
                    .invoke(span, t, returnValue);
            }
        }
    }

    /**
     * Instruments:
     * <ul>
     *     <li>{@link PreparedStatement#executeUpdate()} </li>
     *     <li>{@link PreparedStatement#executeLargeUpdate()} ()} (java8)</li>
     * </ul>
     */
    public static class ExecuteUpdateNoQueryInstrumentation extends StatementInstrumentation {
        public ExecuteUpdateNoQueryInstrumentation() {
            super(
                named("executeUpdate").or(named("executeLargeUpdate"))
                    .and(takesArguments(0))
                    .and(isPublic())
            );
        }

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static Span onBeforeExecute(@Advice.Origin Class<?> clazz, @Advice.This Statement statement) throws Throwable {
            if (MethodHandleDispatcher.USE_REFLECTION) {
                return (Span) MethodHandleDispatcher
                    .getMethod(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#createJdbcSpanLookupSql")
                    .invoke(null, statement, true);
            } else {
                return (Span) MethodHandleDispatcher
                    .getMethodHandle(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#createJdbcSpanLookupSql")
                    .invoke(statement, true);
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onAfterExecute(@Advice.Origin Class<?> clazz,
                                           @Advice.Enter @Nullable Span span,
                                           @Advice.Thrown @Nullable Throwable t,
                                           @Advice.Return long returnValue /* bytebuddy converts int to long for us here ! */) throws Throwable {
            if (MethodHandleDispatcher.USE_REFLECTION) {
                MethodHandleDispatcher
                    .getMethod(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#onAfter")
                    .invoke(null, span, t, returnValue);
            } else {
                MethodHandleDispatcher
                    .getMethodHandle(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#onAfter")
                    .invoke(span, t, returnValue);
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
        public ExecutePreparedStatementInstrumentation() {
            super(
                named("execute").or(named("executeQuery"))
                    .and(takesArguments(0))
                    .and(isPublic())
            );
        }

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static Span onBeforeExecute(@Advice.Origin Class<?> clazz, @Advice.This Statement statement) throws Throwable {
            if (MethodHandleDispatcher.USE_REFLECTION) {
                return (Span) MethodHandleDispatcher
                    .getMethod(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#createJdbcSpanLookupSql")
                    .invoke(null, statement, true);
            } else {
                return (Span) MethodHandleDispatcher
                    .getMethodHandle(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#createJdbcSpanLookupSql")
                    .invoke(statement, true);
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onAfterExecute(@Advice.Origin Class<?> clazz,
                                           @Advice.This Statement statement,
                                           @Advice.Enter @Nullable Span span,
                                           @Advice.Thrown @Nullable Throwable t) throws Throwable {
            if (MethodHandleDispatcher.USE_REFLECTION) {
                MethodHandleDispatcher
                    .getMethod(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#onAfterPreparedStatementExecuteQuery")
                    .invoke(null, statement, span, t);
            } else {
                MethodHandleDispatcher
                    .getMethodHandle(clazz, "co.elastic.apm.agent.jdbc.helper.AdviceHelperAdapter#onAfterPreparedStatementExecuteQuery")
                    .invoke(statement, span, t);
            }
        }
    }

}
