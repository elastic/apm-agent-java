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
package co.elastic.apm.agent.jdbc.helper;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.RegisterMethodHandle;
import co.elastic.apm.agent.impl.transaction.Span;

import javax.annotation.Nullable;
import java.sql.Statement;

public class AdviceHelperAdapter {

    private static final JdbcHelper INSTANCE = new JdbcHelper(ElasticApmInstrumentation.tracer);

    @RegisterMethodHandle
    public static void mapStatementToSql(Statement statement, String sql) {
        INSTANCE.mapStatementToSql(statement, sql);
    }

    @RegisterMethodHandle
    public static void clearInternalStorage() {
        INSTANCE.clearInternalStorage();
    }

    @Nullable
    @RegisterMethodHandle
    public static Span createJdbcSpanLookupSql(Statement statement, boolean preparedStatement) {
        return INSTANCE.createJdbcSpanLookupSql(statement, preparedStatement);
    }

    @Nullable
    @RegisterMethodHandle
    public static Span createJdbcSpan(@Nullable String sql, Statement statement, boolean preparedStatement) {
        return INSTANCE.createJdbcSpan(sql, statement, preparedStatement);
    }

    @RegisterMethodHandle
    public static void onAfterExecuteQuery(Statement statement, @Nullable Span span, @Nullable Throwable t) {
        INSTANCE.onAfterExecuteQuery(statement, span, t);
    }

    @RegisterMethodHandle
    public static void onAfterExecuteBatch(@Nullable Span span, Throwable t, @Nullable Object returnValue) {
        INSTANCE.onAfterExecuteBatch(span, t, returnValue);
    }

    @RegisterMethodHandle
    public static void onAfterPreparedStatementExecuteQuery(Statement statement, @Nullable Span span, @Nullable Throwable t) {
        INSTANCE.onAfterPreparedStatementExecuteQuery(statement, span, t);
    }

    @RegisterMethodHandle
    public static void onAfter(@Nullable Span span, @Nullable Throwable t, long affectedCount) {
        INSTANCE.onAfter(span, t, affectedCount);
    }
}
