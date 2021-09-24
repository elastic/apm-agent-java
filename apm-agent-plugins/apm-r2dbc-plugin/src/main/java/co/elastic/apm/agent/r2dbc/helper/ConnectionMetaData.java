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
import io.r2dbc.spi.ConnectionFactoryOptions;
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
    public static ConnectionMetaData create(@Nullable String databaseProductName, @Nullable String databaseVersion, @Nullable ConnectionFactoryOptions connectionFactoryOptions) {
        logger.trace("Trying to define connection metadata by: productName = [{}], version = [{}], options = [{}]", databaseProductName, databaseVersion, connectionFactoryOptions);
        String dbVendor = null;
        if (connectionFactoryOptions != null) {
            Object driverValue = connectionFactoryOptions.getValue(ConnectionFactoryOptions.DRIVER);
            if (driverValue instanceof String) {
                String driver = (String) driverValue;
                if (!driver.isEmpty()) {
                    dbVendor = driver;
                    logger.trace("Defined driver from connection factory options = {}", dbVendor);
                }
            }
        }
        if (dbVendor == null && databaseProductName != null) {
            String databaseNameLower = databaseProductName.toLowerCase();
            for (MetadataParser parser : MetadataParser.values()) {
                if (databaseNameLower.contains(parser.containsPart)) {
                    dbVendor = parser.dbVendor;
                    logger.trace("Defined driver from metadata = {}", dbVendor);
                    break;
                }
            }
        }
        if (dbVendor == null) {
            dbVendor = "unknown";
        }

        ConnectionMetaData ret = null;
        MetadataParser metadataParser = parsers.get(dbVendor);
        if (metadataParser == null) {
            logger.trace("Not found metadata parser. Will be used unknown.");
            metadataParser = parsers.get("unknown");
        }
        try {
            ret = metadataParser.parse(databaseProductName, databaseVersion, connectionFactoryOptions);
        } catch (Exception e) {
            logger.error("Failed to parse for dbVendor {}", dbVendor, e);
        }
        if (logger.isDebugEnabled()) {
            logger.trace("Based on the dbVendor {}, parsed metadata is: {}", dbVendor, ret);
        }
        return ret;
    }

    private String dbProductName;
    private String dbVendor;
    private String dbVersion;
    private String host;
    private int port;
    private String instance;
    private String user;

    public ConnectionMetaData() {}

    public String getDbVendor() {
        return dbVendor;
    }

    public int getPort() {
        return port;
    }

    public String getDbVersion() {
        return dbVersion;
    }

    @Nullable
    public String getHost() {
        return host;
    }

    @Nullable
    public String getInstance() {
        return instance;
    }

    public String getUser() {
        return user;
    }

    public ConnectionMetaData withDbProductName(@Nullable String dbProductName) {
        this.dbProductName = dbProductName;
        return this;
    }

    public ConnectionMetaData withDbVendor(@Nonnull String dbVendor) {
        this.dbVendor = dbVendor;
        return this;
    }

    public ConnectionMetaData withDbVersion(@Nullable String dbVersion) {
        this.dbVersion = dbVersion;
        return this;
    }

    public ConnectionMetaData withHost(@Nullable String host) {
        this.host = host;
        return this;
    }

    public ConnectionMetaData withPort(int port) {
        this.port = port;
        return this;
    }

    public ConnectionMetaData withInstance(@Nullable String instance) {
        this.instance = instance;
        return this;
    }

    public ConnectionMetaData withUser(@Nullable String user) {
        this.user = user;
        return this;
    }

    @Override
    public String toString() {
        return "ConnectionMetaData{" +
            "dbProductName='" + dbProductName + '\'' +
            ", dbVendor='" + dbVendor + '\'' +
            ", dbVersion='" + dbVersion + '\'' +
            ", host='" + host + '\'' +
            ", port=" + port +
            ", instance='" + instance + '\'' +
            ", user='" + user + '\'' +
            '}';
    }

    private enum MetadataParser {
        ORACLE("oracle", "oracle") {

            @Override
            ConnectionMetaData parse(@Nullable String databaseProductName, @Nullable String metadataDatabaseVersion, @Nullable ConnectionFactoryOptions connectionFactoryOptions) {
                return toMetadata(dbVendor, 1521, databaseProductName, metadataDatabaseVersion, connectionFactoryOptions);
            }
        },

        POSTGRESQL("postgresql", "postgresql") {

            @Override
            ConnectionMetaData parse(@Nullable String databaseProductName, @Nullable String metadataDatabaseVersion, @Nullable ConnectionFactoryOptions connectionFactoryOptions) {
                return toMetadata(dbVendor, 5432, databaseProductName, metadataDatabaseVersion, connectionFactoryOptions);
            }
        },

        MYSQL("mysql", "mysql") {

            @Override
            ConnectionMetaData parse(@Nullable String databaseProductName, @Nullable String metadataDatabaseVersion, @Nullable ConnectionFactoryOptions connectionFactoryOptions) {
                return toMetadata(dbVendor,  3306, databaseProductName, metadataDatabaseVersion, connectionFactoryOptions);
            }
        },

        H2("h2", "h2") {

            @Override
            ConnectionMetaData parse(@Nullable String databaseProductName, @Nullable String metadataDatabaseVersion, @Nullable ConnectionFactoryOptions connectionFactoryOptions) {
                return toMetadata(dbVendor, -1, databaseProductName, metadataDatabaseVersion, connectionFactoryOptions);
            }
        },

        MARIADB("mariadb", "mariadb") {

            @Override
            ConnectionMetaData parse(@Nullable String databaseProductName, @Nullable String metadataDatabaseVersion, @Nullable ConnectionFactoryOptions connectionFactoryOptions) {
                return toMetadata(dbVendor, 3306, databaseProductName, metadataDatabaseVersion, connectionFactoryOptions);
            }
        },

        SQLSERVER("sqlserver", "sql server") {

            @Override
            ConnectionMetaData parse(@Nullable String databaseProductName, @Nullable String metadataDatabaseVersion, @Nullable ConnectionFactoryOptions connectionFactoryOptions) {
                return toMetadata(dbVendor, 1433, databaseProductName, metadataDatabaseVersion, connectionFactoryOptions);
            }
        },

        UNKNOWN("unknown", "unknown") {

            @Override
            ConnectionMetaData parse(@Nullable String databaseProductName, @Nullable String metadataDatabaseVersion, @Nullable ConnectionFactoryOptions connectionFactoryOptions) {
                return toMetadata(dbVendor, -1, databaseProductName, metadataDatabaseVersion, connectionFactoryOptions);
            }
        };

        MetadataParser(String dbVendor, String containsPart) {
            this.dbVendor = dbVendor;
            this.containsPart = containsPart;
        }

        final String dbVendor;
        final String containsPart;

        abstract ConnectionMetaData parse(@Nullable String databaseProductName, @Nullable String metadataDatabaseVersion, @Nullable ConnectionFactoryOptions connectionFactoryOptions);

        private static ConnectionMetaData toMetadata(@Nonnull String dbVendor, int defaultPort, @Nullable String databaseProductName, @Nullable String metadataDatabaseVersion, @Nullable ConnectionFactoryOptions connectionFactoryOptions) {
            String database = null, host = null, user = null;
            int port = defaultPort;
            if (connectionFactoryOptions != null) {
                Object dbOption = connectionFactoryOptions.getValue(ConnectionFactoryOptions.DATABASE);
                Object hostOption = connectionFactoryOptions.getValue(ConnectionFactoryOptions.HOST);
                Object portOption = connectionFactoryOptions.getValue(ConnectionFactoryOptions.PORT);
                Object userOption = connectionFactoryOptions.getValue(ConnectionFactoryOptions.USER);
                if (dbOption instanceof String) {
                    database = (String) dbOption;
                }
                if (hostOption instanceof String) {
                    host = (String) hostOption;
                }
                if (portOption instanceof Integer) {
                    port = (int) portOption;
                }
                if (userOption instanceof String) {
                    user = (String) userOption;
                }
            }
            return new ConnectionMetaData()
                .withDbVendor(dbVendor)
                .withDbProductName(databaseProductName)
                .withDbVersion(metadataDatabaseVersion)
                .withHost(host)
                .withPort(port)
                .withUser(user)
                .withInstance(database);
        }
    }
}
