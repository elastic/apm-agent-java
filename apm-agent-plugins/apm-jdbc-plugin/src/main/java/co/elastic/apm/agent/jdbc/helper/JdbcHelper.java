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

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.util.DataStructures;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;

import javax.annotation.Nullable;
import java.sql.Connection;

public abstract class JdbcHelper {
    @SuppressWarnings("WeakerAccess")
    @VisibleForAdvice
    public static final WeakConcurrentMap<Object, String> statementSqlMap = DataStructures.createWeakConcurrentMapWithCleanerThread();

    /**
     * Maps the provided sql to the provided Statement object
     *
     * @param statement javax.sql.Statement object
     * @param sql       query string
     */
    public static void mapStatementToSql(Object statement, String sql) {
        statementSqlMap.putIfAbsent(statement, sql);
    }

    /**
     * Returns the SQL statement belonging to provided Statement.
     * <p>
     * Might return {@code null} when the provided Statement is a wrapper of the actual statement.
     * </p>
     *
     * @return the SQL statement belonging to provided Statement, or {@code null}
     */
    @Nullable
    public static String retrieveSqlForStatement(Object statement) {
        return statementSqlMap.get(statement);
    }


    @Nullable
    public abstract Span createJdbcSpan(@Nullable String sql, Connection connection, @Nullable TraceContextHolder<?> parent, boolean preparedStatement);
}
