/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.jdbc.helper;

import co.elastic.apm.impl.transaction.AbstractSpan;
import co.elastic.apm.impl.transaction.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import static co.elastic.apm.jdbc.JdbcUtils.DB_SPAN_TYPE_PREFIX;
import static co.elastic.apm.jdbc.JdbcUtils.computeJdbcSpanTypeName;

public class JdbcHelperImpl implements JdbcHelper {

    private static final Logger logger = LoggerFactory.getLogger(JdbcHelperImpl.class);
    private static final Map<Connection, ConnectionMetaData> metaDataMap =
        Collections.synchronizedMap(new WeakHashMap<Connection, ConnectionMetaData>());

    private static final String UNKNOWN_SPAN_TYPE = computeJdbcSpanTypeName("unknown");

    @Override
    @Nullable
    public Span createJdbcSpan(@Nullable String sql, Connection connection, @Nullable AbstractSpan<?> parent) {
        if (sql == null || isAlreadyMonitored(parent) || parent == null || !parent.isSampled()) {
            return null;
        }
        Span span = parent.createSpan().activate();
        span.setName(getMethod(sql));
        // temporarily setting the type here is important
        // getting the meta data can result in another jdbc call
        // if that is traced as well -> StackOverflowError
        // to work around that, isAlreadyMonitored checks if the parent span is a db span and ignores them
        span.withType(UNKNOWN_SPAN_TYPE);
        try {
            final ConnectionMetaData connectionMetaData = getConnectionMetaData(connection);
            span.withType(connectionMetaData.type);
            span.getContext().getDb()
                .withUser(connectionMetaData.user)
                .withStatement(sql)
                .withType("sql");
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
    private boolean isAlreadyMonitored(@Nullable AbstractSpan<?> parent) {
        if (!(parent instanceof Span)) {
            return false;
        }
        Span parentSpan = (Span) parent;
        // a db span can't be the child of another db span
        // this means the span has already been created for this db call
        return parentSpan.getType() != null && parentSpan.getType().startsWith(DB_SPAN_TYPE_PREFIX);
    }


    private ConnectionMetaData getConnectionMetaData(Connection connection) throws SQLException {
        ConnectionMetaData connectionMetaData = metaDataMap.get(connection);
        if (connectionMetaData == null) {
            final DatabaseMetaData metaData = connection.getMetaData();
            String dbVendor = getDbVendor(metaData.getURL());
            connectionMetaData = new ConnectionMetaData(computeJdbcSpanTypeName(dbVendor), metaData.getUserName());
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
        final String type;
        final String user;

        private ConnectionMetaData(String type, String user) {
            this.type = type;
            this.user = user;
        }
    }
}
