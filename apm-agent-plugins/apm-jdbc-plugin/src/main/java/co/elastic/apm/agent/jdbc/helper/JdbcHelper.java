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
package co.elastic.apm.agent.jdbc.helper;

import co.elastic.apm.agent.db.signature.Scanner;
import co.elastic.apm.agent.db.signature.SignatureParser;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.jdbc.JdbcFilter;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Callable;

import static co.elastic.apm.agent.jdbc.helper.JdbcGlobalState.metaDataMap;
import static co.elastic.apm.agent.jdbc.helper.JdbcGlobalState.statementSqlMap;

public class JdbcHelper {

    private static final Logger logger = LoggerFactory.getLogger(JdbcHelper.class);
    public static final String DB_SPAN_TYPE = "db";
    public static final String DB_SPAN_ACTION = "query";

    private static final JdbcHelper INSTANCE = new JdbcHelper();

    public static JdbcHelper get() {
        return INSTANCE;
    }

    private final SignatureParser signatureParser = new SignatureParser(new Callable<Scanner>() {
        @Override
        public Scanner call() {
            return new Scanner(new JdbcFilter());
        }
    });

    /**
     * Maps the provided sql to the provided Statement object
     *
     * @param statement javax.sql.Statement object
     * @param sql       query string
     */
    public void mapStatementToSql(Object statement, String sql) {
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
    public String retrieveSqlForStatement(Object statement) {
        return statementSqlMap.get(statement);
    }


    @Nullable
    public Span<?> createJdbcSpan(@Nullable String sql, Object statement, @Nullable AbstractSpan<?> parent, boolean preparedStatement) {
        if (!(statement instanceof Statement) || sql == null || isAlreadyMonitored(parent) || parent == null) {
            return null;
        }

        Span<?> span = parent.createExitSpan();
        if (span == null) {
            return null;
        } else {
            span.activate();
        }

        if (sql.isEmpty()) {
            span.withName("empty query");
        } else if (span.isSampled()) {
            StringBuilder spanName = span.getAndOverrideName(AbstractSpan.PRIORITY_DEFAULT);
            if (spanName != null) {
                signatureParser.querySignature(sql, spanName, preparedStatement);
            }
        }
        // setting the type here is important
        // getting the meta data can result in another jdbc call
        // if that is traced as well -> StackOverflowError
        // to work around that, isAlreadyMonitored checks if the parent span is a db span and ignores them
        span.withType(DB_SPAN_TYPE);

        // write fields that do not rely on metadata
        span.getContext().getDb()
            .withStatement(sql.isEmpty() ? "(empty query)" : sql)
            .withType("sql");

        Connection connection = safeGetConnection((Statement) statement);
        ConnectionMetaData connectionMetaData = getConnectionMetaData(connection);

        String vendor = "unknown";
        if (connectionMetaData != null) {
            vendor = connectionMetaData.getDbVendor();
            String instance = connectionMetaData.getInstance();
            span.getContext().getDb()
                .withInstance(instance)
                .withUser(connectionMetaData.getUser());

            span.getContext().getDestination()
                .withAddress(connectionMetaData.getHost())
                .withPort(connectionMetaData.getPort());

            span.getContext().getServiceTarget()
                .withType(vendor)
                .withName(instance);
        }
        span.withSubtype(vendor).withAction(DB_SPAN_ACTION);

        return span;
    }

    /*
     * This makes sure that even when there are wrappers for the statement,
     * we only record each JDBC call once.
     */
    private boolean isAlreadyMonitored(@Nullable AbstractSpan<?> parent) {
        if (!(parent instanceof Span<?>)) {
            return false;
        }
        Span<?> parentSpan = (Span<?>) parent;
        // a db span can't be the child of another db span
        // this means the span has already been created for this db call
        return parentSpan.getType() != null && parentSpan.getType().equals(DB_SPAN_TYPE);
    }

    /**
     * Build or return cached connection metadata. The returned value might rely on current connection state for the
     * database instance and the database user. For database instance, the value is parsed from JDBC connection string
     * and the runtime value of {@link Connection#getCatalog()} is used as a fallback when parsing is unable to capture
     * the database name.
     *
     * @param connection database connection
     * @return connection metadata, either from cache or from current connection state
     */
    @Nullable
    private ConnectionMetaData getConnectionMetaData(@Nullable Connection connection) {
        if (null == connection) {
            return null;
        }

        ConnectionMetaData connectionMetaData = metaDataMap.get(connection);
        if (connectionMetaData != null) {
            return connectionMetaData;
        }

        Class<?> type = connection.getClass();
        Boolean supported = isSupported(JdbcFeature.METADATA, type);
        if (supported == Boolean.FALSE) {
            return null;
        }

        try {
            DatabaseMetaData metaData = connection.getMetaData();
            connectionMetaData = ConnectionMetaData.parse(metaData.getURL())
                .withConnectionInstance(safeGetCatalog(connection))
                .withConnectionUser(metaData.getUserName())
                .build();

            if (logger.isDebugEnabled()) {
                logger.debug("Based on the connection URL {}, parsed metadata is: {}", metaData.getURL(), connectionMetaData);
            }

            if (supported == null) {
                markSupported(JdbcFeature.METADATA, type);
            }
        } catch (SQLException e) {
            markNotSupported(JdbcFeature.METADATA, type, e);
        }

        if (connectionMetaData != null) {
            metaDataMap.put(connection, connectionMetaData);
        }
        return connectionMetaData;
    }

    @Nullable
    private String safeGetCatalog(Connection connection) {
        String catalog = null;
        Class<?> type = connection.getClass();
        Boolean supported = isSupported(JdbcFeature.CATALOG, type);
        if (supported == Boolean.FALSE) {
            return null;
        }

        try {
            catalog = connection.getCatalog();
            markSupported(JdbcFeature.CATALOG, type);
        } catch (SQLException e) {
            markNotSupported(JdbcFeature.CATALOG, type, e);
        }

        return catalog;
    }

    @Nullable
    private Connection safeGetConnection(Statement statement) {
        Connection connection = null;
        Class<?> type = statement.getClass();
        Boolean supported = isSupported(JdbcFeature.CONNECTION, type);
        if (supported == Boolean.FALSE) {
            return null;
        }

        try {
            connection = statement.getConnection();
            if (supported == null) {
                markSupported(JdbcFeature.CONNECTION, type);
            }
        } catch (SQLException e) {
            markNotSupported(JdbcFeature.CONNECTION, type, e);
        }

        return connection;
    }

    @Nullable
    private static Boolean isSupported(JdbcFeature feature, Class<?> type) {
        return feature.classSupport.get(type);
    }

    private static void markSupported(JdbcFeature feature, Class<?> type) {
        feature.classSupport.put(type, Boolean.TRUE);
    }

    private static void markNotSupported(JdbcFeature feature, Class<?> type, SQLException e) {
        Boolean previous = feature.classSupport.put(type, Boolean.FALSE);
        if (previous == null) {
            logger.warn("JDBC feature not supported on class " + type, e);
        }
    }

    public void removeSqlForStatement(Statement statement) {
        statementSqlMap.remove(statement);
    }

    /**
     * Represent JDBC features for which availability has to be checked at runtime
     */
    private enum JdbcFeature {
        /**
         * {@link Connection#getMetaData()}
         */
        METADATA(JdbcGlobalState.metadataSupported),
        /**
         * {@link Connection#getCatalog()}
         */
        CATALOG(JdbcGlobalState.catalogSupported),
        /**
         * {@link Statement#getConnection()}
         */
        CONNECTION(JdbcGlobalState.connectionSupported);

        private final WeakMap<Class<?>, Boolean> classSupport;

        JdbcFeature(WeakMap<Class<?>, Boolean> map) {
            this.classSupport = map;
        }
    }

}
