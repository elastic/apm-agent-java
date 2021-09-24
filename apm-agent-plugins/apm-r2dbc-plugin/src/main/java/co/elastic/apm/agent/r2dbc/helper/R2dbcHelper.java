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
package co.elastic.apm.agent.r2dbc.helper;

import co.elastic.apm.agent.db.signature.Scanner;
import co.elastic.apm.agent.db.signature.SignatureParser;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionMetadata;
import io.r2dbc.spi.R2dbcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.Callable;

import static co.elastic.apm.agent.r2dbc.helper.R2dbcGlobalState.batchConnectionMap;
import static co.elastic.apm.agent.r2dbc.helper.R2dbcGlobalState.connectionFactoryMap;
import static co.elastic.apm.agent.r2dbc.helper.R2dbcGlobalState.connectionOptionsMap;
import static co.elastic.apm.agent.r2dbc.helper.R2dbcGlobalState.r2dbcMetaDataMap;
import static co.elastic.apm.agent.r2dbc.helper.R2dbcGlobalState.statementConnectionMap;

public class R2dbcHelper {

    private static final Logger logger = LoggerFactory.getLogger(R2dbcHelper.class);
    public static final String DB_SPAN_TYPE = "db";
    public static final String DB_SPAN_ACTION = "query";

    private static final R2dbcHelper INSTANCE = new R2dbcHelper();

    public static R2dbcHelper get() {
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
     */
    public void mapStatementToSql(Object statement, @Nonnull Object connection, @Nullable String sql) {
        statementConnectionMap.putIfAbsent(statement, new Object[]{connection, sql});
    }

    public void mapBatch(@Nonnull Object batch, @Nonnull Object connection) {
        logger.debug("Trying to map batch = {}", batch);
        // via Batch#add we will add first sql statement
        batchConnectionMap.putIfAbsent(batch, new Object[]{connection, null});
    }

    public void mapConnectionFactoryData(@Nonnull ConnectionFactory connectionFactory, @Nonnull ConnectionFactoryOptions connectionFactoryOptions) {
        logger.debug("Trying to map connection factory {} with options {}", connectionFactory, connectionFactoryOptions);
        connectionFactoryMap.putIfAbsent(connectionFactory, connectionFactoryOptions);
    }

    public void mapConnectionOptionsData(@Nonnull Connection connection, @Nonnull ConnectionFactoryOptions connectionFactoryOptions) {
        logger.debug("Trying to map connection {} with options {}", connection, connectionFactoryOptions);
        connectionOptionsMap.putIfAbsent(connection, connectionFactoryOptions);
    }

    @Nullable
    public ConnectionFactoryOptions getConnectionFactoryOptions(@Nonnull ConnectionFactory connectionFactory) {
        logger.debug("Trying to get options by connection factory {}", connectionFactory);
        return connectionFactoryMap.get(connectionFactory);
    }

    @Nullable
    public ConnectionFactoryOptions getConnectionOptions(@Nonnull Connection connection) {
        logger.debug("Trying to get options by connection {}", connection);
        return connectionOptionsMap.get(connection);
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
    public Object[] retrieveConnectionForStatement(Object statement) {
        return statementConnectionMap.get(statement);
    }

    @Nullable
    public Object[] retrieveMetaForBatch(Object batch) {
        return batchConnectionMap.get(batch);
    }

    @Nullable
    public Span createR2dbcSpan(@Nullable Connection connection, @Nullable String sql, @Nullable AbstractSpan<?> parent) {
        if (sql == null || parent == null) {
            return null;
        }
        Span span = parent.createSpan().activate();
        if (sql.isEmpty()) {
            span.withName("empty query");
        } else if (span.isSampled()) {
            StringBuilder spanName = span.getAndOverrideName(AbstractSpan.PRIO_DEFAULT);
            if (spanName != null) {
                signatureParser.querySignature(sql, spanName, false);
            }
        }
        span.withType(DB_SPAN_TYPE);

        span.getContext().getDb()
            .withStatement(sql.isEmpty() ? "(empty query)" : sql)
            .withType("sql");

        ConnectionMetaData connectionMetaData = getConnectionMetaData(connection);
        logger.debug("Parsed connection metadata = {}", connectionMetaData);
        String vendor = "unknown";
        if (connectionMetaData != null) {
            vendor = connectionMetaData.getDbVendor();
            span.getContext().getDb()
                .withInstance(connectionMetaData.getInstance())
                .withUser(connectionMetaData.getUser());
            Destination destination = span.getContext().getDestination()
                .withAddress(connectionMetaData.getHost())
                .withPort(connectionMetaData.getPort());
            destination.getService()
                .withName(vendor)
                .withResource(vendor)
                .withType(DB_SPAN_TYPE);
        }
        span.withSubtype(vendor).withAction(DB_SPAN_ACTION);
        return span;
    }

    /*
     * This makes sure that even when there are wrappers for the statement,
     * we only record each JDBC call once.
     */
    private boolean isAlreadyMonitored(@Nullable AbstractSpan<?> parent) {
        if (!(parent instanceof Span)) {
            return false;
        }
        Span parentSpan = (Span) parent;
        // a db span can't be the child of another db span
        // this means the span has already been created for this db call
        return parentSpan.getType() != null && parentSpan.getType().equals(DB_SPAN_TYPE);
    }

    @Nullable
    private ConnectionMetaData getConnectionMetaData(@Nullable Connection connection) {
        if (null == connection) {
            return null;
        }
        ConnectionMetaData connectionMetaData = r2dbcMetaDataMap.get(connection);
        if (connectionMetaData != null) {
            logger.debug("Already contains connection metadata in cache.");
            return connectionMetaData;
        }

        Class<?> type = connection.getClass();
        Boolean supported = isSupported(R2dbcFeature.METADATA, type);
        if (supported == Boolean.FALSE) {
            return null;
        }
        ConnectionMetaData apmConnectionMetadata = null;
        try {
            R2dbcHelper helper = R2dbcHelper.get();
            ConnectionMetadata metaData = connection.getMetadata();
            ConnectionFactoryOptions connectionFactoryOptions = helper.getConnectionOptions(connection);
            String databaseProductName = metaData != null ? metaData.getDatabaseProductName() : null;
            String databaseVersion = metaData != null ? metaData.getDatabaseVersion() : null;
            apmConnectionMetadata = ConnectionMetaData.create(databaseProductName, databaseVersion, connectionFactoryOptions);
            if (apmConnectionMetadata != null && supported == null) {
                markSupported(R2dbcFeature.METADATA, type);
            }
        } catch (R2dbcException e) {
            markNotSupported(R2dbcFeature.METADATA, type, e);
        }
        if (apmConnectionMetadata != null) {
            r2dbcMetaDataMap.put(connection, apmConnectionMetadata);
        }
        return apmConnectionMetadata;
    }

    @Nullable
    private static Boolean isSupported(R2dbcFeature feature, Class<?> type) {
        return feature.classSupport.get(type);
    }

    private static void markSupported(R2dbcFeature feature, Class<?> type) {
        feature.classSupport.put(type, Boolean.TRUE);
    }

    private static void markNotSupported(R2dbcFeature feature, Class<?> type, R2dbcException e) {
        Boolean previous = feature.classSupport.put(type, Boolean.FALSE);
        if (previous == null) {
            logger.warn("R2DBC feature not supported on class " + type, e);
        }
    }

    /**
     * Represent JDBC features for which availability has to be checked at runtime
     */
    private enum R2dbcFeature {
        METADATA(R2dbcGlobalState.metadataSupported);

        private final WeakConcurrentMap<Class<?>, Boolean> classSupport;

        R2dbcFeature(WeakConcurrentMap<Class<?>, Boolean> map) {
            this.classSupport = map;
        }
    }

}
