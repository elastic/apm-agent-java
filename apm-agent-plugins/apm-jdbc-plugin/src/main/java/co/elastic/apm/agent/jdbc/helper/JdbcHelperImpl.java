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
package co.elastic.apm.agent.jdbc.helper;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.jdbc.signature.SignatureParser;
import co.elastic.apm.agent.util.DataStructures;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

public class JdbcHelperImpl extends JdbcHelper {
    public static final String DB_SPAN_TYPE = "db";
    public static final String DB_SPAN_ACTION = "query";

    private static final Logger logger = LoggerFactory.getLogger(JdbcHelperImpl.class);
    private static final WeakConcurrentMap<Connection, ConnectionMetaData> metaDataMap = DataStructures.createWeakConcurrentMapWithCleanerThread();

    @VisibleForAdvice
    public static final ThreadLocal<SignatureParser> SIGNATURE_PARSER_THREAD_LOCAL = new ThreadLocal<SignatureParser>() {
        @Override
        protected SignatureParser initialValue() {
            return new SignatureParser();
        }
    };

    @Override
    @Nullable
    public Span createJdbcSpan(@Nullable String sql, Connection connection, @Nullable TraceContextHolder<?> parent, boolean preparedStatement) {
        if (sql == null || isAlreadyMonitored(parent) || parent == null || !parent.isSampled()) {
            return null;
        }
        Span span = parent.createSpan().activate();
        StringBuilder spanName = span.getAndOverrideName(AbstractSpan.PRIO_DEFAULT);
        if (spanName != null) {
            SIGNATURE_PARSER_THREAD_LOCAL.get().querySignature(sql, spanName, preparedStatement);
        }
        // setting the type here is important
        // getting the meta data can result in another jdbc call
        // if that is traced as well -> StackOverflowError
        // to work around that, isAlreadyMonitored checks if the parent span is a db span and ignores them
        span.withType(DB_SPAN_TYPE);
        try {
            final ConnectionMetaData connectionMetaData = getConnectionMetaData(connection);
            span.withSubtype(connectionMetaData.dbVendor)
                .withAction(DB_SPAN_ACTION);
            span.getContext().getDb()
                .withUser(connectionMetaData.user)
                .withStatement(sql)
                .withType("sql");
            span.getContext().getDestination().getService()
                .withName(connectionMetaData.dbVendor)
                .withResource(connectionMetaData.dbVendor)
                .withType(DB_SPAN_TYPE);
        } catch (SQLException e) {
            logger.warn("Ignored exception", e);
        }
        return span;
    }

    @Nullable
    private String getMethod(@Nullable String sql) {
        if (sql == null) {
            return null;
        }
        // don't allocate objects for the common case
        if (sql.startsWith("SELECT") || sql.startsWith("select")) {
            return "SELECT";
        }
        sql = sql.trim();
        final int indexOfWhitespace = sql.indexOf(' ');
        if (indexOfWhitespace > 0) {
            return sql.substring(0, indexOfWhitespace).toUpperCase();
        } else {
            // for example COMMIT
            return sql.toUpperCase();
        }
    }

    /*
     * This makes sure that even when there are wrappers for the statement,
     * we only record each JDBC call once.
     */
    private boolean isAlreadyMonitored(@Nullable TraceContextHolder<?> parent) {
        if (!(parent instanceof Span)) {
            return false;
        }
        Span parentSpan = (Span) parent;
        // a db span can't be the child of another db span
        // this means the span has already been created for this db call
        return parentSpan.getType() != null && parentSpan.getType().equals(DB_SPAN_TYPE);
    }


    private ConnectionMetaData getConnectionMetaData(Connection connection) throws SQLException {
        ConnectionMetaData connectionMetaData = metaDataMap.get(connection);
        if (connectionMetaData == null) {
            final DatabaseMetaData metaData = connection.getMetaData();
            String dbVendor = getDbVendor(metaData.getURL());
            connectionMetaData = new ConnectionMetaData(dbVendor, metaData.getUserName());
            metaDataMap.put(connection, connectionMetaData);
        }
        return connectionMetaData;

    }

    private String getDbVendor(String url) {
        // jdbc:h2:mem:test
        //     ^
        int indexOfJdbc = url.indexOf("jdbc:");
        if (indexOfJdbc != -1) {
            // h2:mem:test
            String urlWithoutJdbc = url.substring(indexOfJdbc + 5);
            int indexOfColonAfterVendor = urlWithoutJdbc.indexOf(":");
            if (indexOfColonAfterVendor != -1) {
                // h2
                return urlWithoutJdbc.substring(0, indexOfColonAfterVendor);
            }
        }
        return "unknown";
    }

    private static class ConnectionMetaData {
        final String dbVendor;
        final String user;

        private ConnectionMetaData(String dbVendor, String user) {
            this.dbVendor = dbVendor;
            this.user = user;
        }
    }
}
