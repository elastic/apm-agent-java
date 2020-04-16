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
import co.elastic.apm.agent.impl.context.Destination;
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
import java.sql.Statement;

@SuppressWarnings("unused") // indirect access to this class provided through HelperClassManager
public class JdbcHelperImpl extends JdbcHelper {

    private static final Logger logger = LoggerFactory.getLogger(JdbcHelperImpl.class);

    // Important implementation note:
    //
    // because this class is potentially loaded from multiple classloaders, making those fields 'static' will not
    // have the expected behavior, thus, any direct reference to `JdbcHelperImpl` should only be obtained from the
    // HelperClassManager<JdbcHelper> instance.
    private final WeakConcurrentMap<Connection, ConnectionMetaData> metaDataMap = DataStructures.createWeakConcurrentMapWithCleanerThread();
    private final WeakConcurrentMap<Class<?>, Boolean> updateCountSupported = new WeakConcurrentMap.WithInlinedExpunction<Class<?>, Boolean>();
    private final WeakConcurrentMap<Class<?>, Boolean> metadataSupported = new WeakConcurrentMap.WithInlinedExpunction<Class<?>, Boolean>();
    private final WeakConcurrentMap<Class<?>, Boolean> connectionSupported = new WeakConcurrentMap.WithInlinedExpunction<Class<?>, Boolean>();

    @VisibleForAdvice
    public final ThreadLocal<SignatureParser> SIGNATURE_PARSER_THREAD_LOCAL = new ThreadLocal<SignatureParser>() {
        @Override
        protected SignatureParser initialValue() {
            return new SignatureParser();
        }
    };

    @Override
    public void clearInternalStorage() {
        metaDataMap.clear();
        updateCountSupported.clear();
        metadataSupported.clear();
        connectionSupported.clear();
    }

    @Override
    @Nullable
    public Span createJdbcSpan(@Nullable String sql, Object statement, @Nullable TraceContextHolder<?> parent, boolean preparedStatement) {
        if (!(statement instanceof Statement) || sql == null || isAlreadyMonitored(parent) || parent == null) {
            return null;
        }

        Span span = parent.createSpan().activate();
        if (sql.isEmpty()) {
            span.withName("empty query");
        } else if (span.isSampled()) {
            StringBuilder spanName = span.getAndOverrideName(AbstractSpan.PRIO_DEFAULT);
            if (spanName != null) {
                SIGNATURE_PARSER_THREAD_LOCAL.get().querySignature(sql, spanName, preparedStatement);
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
        ConnectionMetaData connectionMetaData = connection == null ? null : getConnectionMetaData(connection);
        if (connectionMetaData != null) {
            span.withSubtype(connectionMetaData.getDbVendor())
                .withAction(DB_SPAN_ACTION);
            span.getContext().getDb()
                .withUser(connectionMetaData.getUser());
            Destination destination = span.getContext().getDestination()
                .withAddress(connectionMetaData.getHost())
                .withPort(connectionMetaData.getPort());
            destination.getService()
                .withName(connectionMetaData.getDbVendor())
                .withResource(connectionMetaData.getDbVendor())
                .withType(DB_SPAN_TYPE);
        }

        return span;
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

    @Nullable
    private ConnectionMetaData getConnectionMetaData(Connection connection) {
        ConnectionMetaData connectionMetaData = metaDataMap.get(connection);
        if (connectionMetaData != null) {
            return connectionMetaData;
        }

        Class<?> type = connection.getClass();
        Boolean supported = isSupported(metadataSupported, type);
        if (supported == Boolean.FALSE) {
            return null;
        }

        try {
            DatabaseMetaData metaData = connection.getMetaData();
            connectionMetaData = ConnectionMetaData.create(metaData.getURL(), metaData.getUserName());
            if (supported == null) {
                markSupported(metadataSupported, type);
            }
        } catch (SQLException e) {
            markNotSupported(metadataSupported, type, e);
        }

        if (connectionMetaData != null) {
            metaDataMap.put(connection, connectionMetaData);
        }
        return connectionMetaData;
    }

    @Nullable
    private Connection safeGetConnection(Statement statement) {
        Connection connection = null;
        Class<?> type = statement.getClass();
        Boolean supported = isSupported(connectionSupported, type);
        if (supported == Boolean.FALSE) {
            return null;
        }

        try {
            connection = statement.getConnection();
            if (supported == null) {
                markSupported(connectionSupported, type);
            }
        } catch (SQLException e) {
            markNotSupported(connectionSupported, type, e);
        }

        return connection;
    }


    @Override
    public long safeGetUpdateCount(Object statement) {
        long result = Long.MIN_VALUE;
        if (!(statement instanceof Statement)) {
            return result;
        }

        Class<?> type = statement.getClass();
        Boolean supported = isSupported(updateCountSupported, type);
        if (supported == Boolean.FALSE) {
            return result;
        }

        try {
            result = ((Statement) statement).getUpdateCount();
            if (supported == null) {
                markSupported(updateCountSupported, type);
            }
        } catch (SQLException e) {
            markNotSupported(updateCountSupported, type, e);
        }

        return result;
    }

    @Nullable
    private static Boolean isSupported(WeakConcurrentMap<Class<?>, Boolean> featureMap, Class<?> type) {
        return featureMap.get(type);
    }

    private static void markSupported(WeakConcurrentMap<Class<?>, Boolean> map, Class<?> type) {
        map.put(type, Boolean.TRUE);
    }

    private static void markNotSupported(WeakConcurrentMap<Class<?>, Boolean> map, Class<?> type, SQLException e) {
        Boolean previous = map.put(type, Boolean.FALSE);
        if (previous == null) {
            logger.warn("JDBC feature not supported on class " + type, e);
        }
    }


}
