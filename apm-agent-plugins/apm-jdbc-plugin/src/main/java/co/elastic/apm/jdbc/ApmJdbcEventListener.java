/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.jdbc;

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.transaction.Span;
import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * @deprecated use javaagent
 */
@Deprecated
public class ApmJdbcEventListener extends SimpleJdbcEventListener {

    private static final Logger logger = LoggerFactory.getLogger(ApmJdbcEventListener.class);
    private static final Map<Connection, ConnectionMetaData> metaDataMap =
        Collections.synchronizedMap(new WeakHashMap<Connection, ConnectionMetaData>());
    private final ElasticApmTracer elasticApmTracer;

    public ApmJdbcEventListener() {
        this(ElasticApmTracer.get());
    }

    public ApmJdbcEventListener(ElasticApmTracer elasticApmTracer) {
        this.elasticApmTracer = elasticApmTracer;
    }

    @Nullable
    static String getMethod(@Nullable String sql) {
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
    private static boolean isAlreadyMonitored(@Nullable Span parent) {
        // a db span can't be the child of another db span
        // this means the span has already been created for this db call
        return parent != null && parent.getType() != null && parent.getType().startsWith("db.");
    }

    @Override
    public void onAfterGetConnection(ConnectionInformation connectionInformation, SQLException e) {
    }

    @Override
    public void onBeforeAnyExecute(StatementInformation statementInformation) {
        createJdbcSpan(statementInformation.getStatementQuery(), statementInformation.getConnectionInformation().getConnection(),
            elasticApmTracer.currentSpan());
    }

    @Nullable
    Span createJdbcSpan(@Nullable String sql, Connection connection, @Nullable Span parentSpan) {
        if (sql == null || isAlreadyMonitored(parentSpan)) {
            return null;
        }
        Span span = elasticApmTracer.startSpan();
        if (span == null) {
            return null;
        }
        span.setName(getMethod(sql));
        // temporarily setting the type here is important
        // getting the meta data can result in another jdbc call
        // if that is traced as well -> StackOverflowError
        // to work around that, isAlreadyMonitored checks if the parent span is a db span and ignores them
        span.setType("db.unknown.sql");
        try {
            final ConnectionMetaData connectionMetaData = getConnectionMetaData(connection);
            span.setType(connectionMetaData.type);
            span.getContext().getDb()
                .withUser(connectionMetaData.user)
                .withStatement(sql)
                .withType("sql");
        } catch (SQLException e) {
            logger.warn("Ignored exception", e);
        }
        return span;
    }

    private ConnectionMetaData getConnectionMetaData(Connection connection) throws SQLException {
        ConnectionMetaData connectionMetaData = metaDataMap.get(connection);
        if (connectionMetaData == null) {
            final DatabaseMetaData metaData = connection.getMetaData();
            String dbVendor = getDbVendor(metaData.getURL());
            connectionMetaData = new ConnectionMetaData("db." + dbVendor + ".sql", metaData.getUserName());
            metaDataMap.put(connection, connectionMetaData);
        }
        return connectionMetaData;

    }

    String getDbVendor(String url) {
        // jdbc:h2:mem:test
        //     ^
        int indexOfJdbc = url.indexOf("jdbc:") + 5;
        if (indexOfJdbc != -1) {
            // h2:mem:test
            String urlWithoutJdbc = url.substring(indexOfJdbc);
            int indexOfColonAfterVendor = urlWithoutJdbc.indexOf(":");
            if (indexOfColonAfterVendor != -1) {
                // h2
                return urlWithoutJdbc.substring(0, indexOfColonAfterVendor);
            }
        }
        return "unknown";
    }

    @Override
    public void onAfterAnyExecute(StatementInformation statementInformation, long timeElapsedNanos, SQLException e) {
        Span span = elasticApmTracer.currentSpan();
        if (span != null) {
            span.end();
        }
    }

    @Override
    public void onBeforeAnyAddBatch(StatementInformation statementInformation) {
        super.onBeforeAnyAddBatch(statementInformation);
    }

    @Override
    public void onAfterAnyAddBatch(StatementInformation statementInformation, long timeElapsedNanos, SQLException e) {
        super.onAfterAnyAddBatch(statementInformation, timeElapsedNanos, e);
    }

    private static class ConnectionMetaData {
        final String type;
        final String user;

        private ConnectionMetaData(String type, String user) {
            this.type = type;
            this.user = user;
        }
    }
}
