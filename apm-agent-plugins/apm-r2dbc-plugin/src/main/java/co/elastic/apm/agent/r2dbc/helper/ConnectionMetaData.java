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

import co.elastic.apm.agent.sdk.state.GlobalState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@GlobalState
public class ConnectionMetaData {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionMetaData.class);

    private static final Map<String, MetadataParser> parsers = new HashMap<>();
    static {
        for (MetadataParser parser : MetadataParser.values()) {
            parsers.put(parser.dbVendor, parser);
        }
    }

    @Nonnull
    public static ConnectionMetaData create(@Nullable String databaseProductName, @Nullable String databaseVersion) {
        logger.debug("Trying to define connection metadata for {}, {}", databaseProductName, databaseVersion);
        String dbVendor = "unknown";
        if (databaseProductName != null) {
            String databaseNameLower = databaseProductName.toLowerCase();
            for (MetadataParser parser : MetadataParser.values()) {
                if (databaseNameLower.contains(parser.containsPart)) {
                    dbVendor = parser.dbVendor;
                    break;
                }
            }
        }

        ConnectionMetaData ret = null;
        MetadataParser metadataParser = parsers.get(dbVendor);
        try {
            ret = metadataParser.parse(databaseProductName, databaseVersion);
        } catch (Exception e) {
            logger.error("Failed to parse databaseProductName: " + databaseProductName, e);
            ret = parsers.get("unknown").parse(databaseProductName, databaseVersion);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Based on the database name {}, parsed metadata is: {}", databaseProductName, ret);
        }
        return ret;
    }

    private final String dbVendor;
    private final int port;
    private final String dbVersion;

    public ConnectionMetaData(String dbVendor, String dbVersion, int port) {
        this.dbVendor = dbVendor;
        this.dbVersion = dbVersion;
        this.port = port;
    }

    public String getDbVendor() {
        return dbVendor;
    }

    public int getPort() {
        return port;
    }

    public String getDbVersion() {
        return dbVersion;
    }

    private enum MetadataParser {
        ORACLE("oracle", "oracle") {

            @Override
            ConnectionMetaData parse(@Nullable String databaseProductName, @Nullable String metadataDatabaseVersion) {
                return new ConnectionMetaData(dbVendor, metadataDatabaseVersion, 1521);
            }
        },

        POSTGRESQL("postgresql", "postgresql") {

            @Override
            ConnectionMetaData parse(@Nullable String databaseProductName, @Nullable String metadataDatabaseVersion) {
                return new ConnectionMetaData(dbVendor, metadataDatabaseVersion, 5432);
            }
        },

        MYSQL("mysql", "mysql") {

            @Override
            ConnectionMetaData parse(@Nullable String databaseProductName, @Nullable String metadataDatabaseVersion) {
                return new ConnectionMetaData(dbVendor, metadataDatabaseVersion, 3306);
            }
        },

        DB2("db2", "db2") {

            @Override
            ConnectionMetaData parse(@Nullable String databaseProductName, @Nullable String metadataDatabaseVersion) {
                return new ConnectionMetaData(dbVendor, metadataDatabaseVersion, 50000);
            }
        },

        H2("h2", "h2") {

            @Override
            ConnectionMetaData parse(@Nullable String databaseProductName, @Nullable String metadataDatabaseVersion) {
                return new ConnectionMetaData(dbVendor, metadataDatabaseVersion, -1);
            }
        },

        DERBY("derby", "derby") {

            @Override
            ConnectionMetaData parse(@Nullable String databaseProductName, @Nullable String metadataDatabaseVersion) {
                return new ConnectionMetaData(dbVendor, metadataDatabaseVersion, 1527);
            }
        },

        HSQLDB("hsqldb", "hsqldb") {

            @Override
            ConnectionMetaData parse(@Nullable String databaseProductName, @Nullable String metadataDatabaseVersion) {
                return new ConnectionMetaData(dbVendor, metadataDatabaseVersion, 9001);
            }
        },

        MARIADB("mariadb", "mariadb") {

            @Override
            ConnectionMetaData parse(@Nullable String databaseProductName, @Nullable String metadataDatabaseVersion) {
                return new ConnectionMetaData(dbVendor, metadataDatabaseVersion, 3306);
            }
        },

        SQLSERVER("sqlserver", "sql server") {

            @Override
            ConnectionMetaData parse(@Nullable String databaseProductName, @Nullable String metadataDatabaseVersion) {
                return new ConnectionMetaData(dbVendor, metadataDatabaseVersion, 1433);
            }
        },

        UNKNOWN("unknown", "unknown") {

            @Override
            ConnectionMetaData parse(@Nullable String databaseProductName, @Nullable String metadataDatabaseVersion) {
                return new ConnectionMetaData(dbVendor, metadataDatabaseVersion, -1);
            }
        };

        MetadataParser(String dbVendor, String containsPart) {
            this.dbVendor = dbVendor;
            this.containsPart = containsPart;
        }

        final String dbVendor;
        final String containsPart;

        abstract ConnectionMetaData parse(@Nullable String databaseProductName, @Nullable String metadataDatabaseVersion);

    }
}
