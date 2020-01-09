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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class ConnectionMetaData {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionMetaData.class);

    private static final Map<String, ConnectionUrlParser> parsers = new HashMap<>();

    static {
        for (ConnectionUrlParser parser : ConnectionUrlParser.values()) {
            parsers.put(parser.dbVendor, parser);
        }
    }

    /**
     * Creates a DB metadata based on the connection URL.
     *
     * @param connectionUrl the connection URL obtained from the JDBC connection
     * @param user          DB user
     * @return metadata of a JDBC connection
     */
    public static ConnectionMetaData create(String connectionUrl, String user) {
        String dbVendor = "unknown";

        // trimming a temp copy, keeping the original for logging purposes
        String tmpUrl = connectionUrl;

        // Connection URLs have a common prefix, starting with "jdbc:", followed by the vendor name and a colon.
        // The rest is vendor specific.
        //
        // Examples:
        // jdbc:postgresql://hostname/db?user=jdo&password=pass
        // jdbc:sqlserver://localhost:32958;sslProtocol=TLS;jaasConfigurationName=SQLJDBCDriver
        // jdbc:oracle:oci:root/secret@localhost:1521:testdb
        // jdbc:derby:memory:testdb
        // jdbc:h2:mem:test
        int indexOfJdbc = tmpUrl.indexOf("jdbc:");

        if (indexOfJdbc != -1) {
            tmpUrl = tmpUrl.substring(indexOfJdbc + 5);
            int indexOfNextColon = tmpUrl.indexOf(":");
            if (indexOfNextColon != -1) {
                dbVendor = tmpUrl.substring(0, indexOfNextColon);
                tmpUrl = tmpUrl.substring(indexOfNextColon + 1);
            }
        }

        // Further parsing needs to be vendor specific.
        ConnectionMetaData ret = null;
        ConnectionUrlParser connectionUrlParser = parsers.get(dbVendor);
        if (connectionUrlParser != null) {
            try {
                ret = connectionUrlParser.parse(tmpUrl, user);
            } catch (Exception e) {
                logger.error("Failed to parse connection URL", e);
            }
        } else {
            // Doesn't hurt to try...
            ret = ConnectionUrlParser.defaultParse(connectionUrl, dbVendor, -1, user);
        }

        if (ret == null) {
            ret = new ConnectionMetaData(dbVendor, null, -1, user);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Based on the connection URL {}, parsed metadata is: {}", connectionUrl, ret);
        }
        return ret;
    }

    private final String dbVendor;
    @Nullable
    private final String host;
    private final int port;
    private final String user;

    private ConnectionMetaData(String dbVendor, @Nullable String host, int port, String user) {
        this.dbVendor = dbVendor;
        this.host = host;
        this.port = port;
        this.user = user;
    }

    public String getDbVendor() {
        return dbVendor;
    }

    @Nullable
    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUser() {
        return user;
    }

    @Override
    public String toString() {
        return "ConnectionMetaData{" +
            "dbVendor='" + dbVendor + '\'' +
            ", host='" + host + '\'' +
            ", port=" + port +
            ", user='" + user + '\'' +
            '}';
    }

    @SuppressWarnings("unused")
    private enum ConnectionUrlParser {
        ORACLE("oracle") {
            public static final int DEFAULT_PORT = 1521;

            @Override
            ConnectionMetaData parse(String connectionUrl, String user) {
                // Examples:
                // jdbc:oracle:thin:scott/tiger@//myhost:1521/myinstance
                // jdbc:oracle:thin:scott/tiger@127.0.0.1:666:myinstance
                // jdbc:oracle:thin:scott/tiger@localhost:myinstance
                // jdbc:oracle:oci:scott/tiger/@
                // jdbc:oracle:thin:@ldap://ldap.acme.com:7777/sales,cn=OracleContext,dc=com
                int indexOfUserDetailsEnd = connectionUrl.indexOf('@');
                if (indexOfUserDetailsEnd > 0) {
                    if (connectionUrl.length() > indexOfUserDetailsEnd + 1) {
                        connectionUrl = connectionUrl.substring(indexOfUserDetailsEnd + 1);
                    } else {
                        // jdbc:oracle:oci:scott/tiger/@
                        return new ConnectionMetaData(dbVendor, null, DEFAULT_PORT, user);
                    }
                }

                String host = null;
                int port = DEFAULT_PORT;

                // try looking for a //host:port/instance pattern
                HostPort hostPort = parseHostPort(connectionUrl);
                if (hostPort.host != null) {
                    host = hostPort.host;
                    if (hostPort.port > 0) {
                        port = hostPort.port;
                    }
                } else {
                    // Thin driver host:port:sid syntax:
                    // myhost:666:instance
                    // myhost:instance
                    // thin:myhost:port:instance
                    if (connectionUrl.startsWith("thin:")) {
                        connectionUrl = connectionUrl.substring("thin:".length());
                    }

                    String[] parts = connectionUrl.split(":");
                    if (parts.length > 0) {
                        host = parts[0];
                    }
                    if (parts.length > 1) {
                        try {
                            port = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) {
                            // apparently not a port...
                        }
                    }
                }

                return new ConnectionMetaData(dbVendor, host, port, user);
            }
        },

        POSTGRESQL("postgresql") {
            @Override
            ConnectionMetaData parse(String connectionUrl, String user) {
                return ConnectionUrlParser.defaultParse(connectionUrl, dbVendor, 5432, user);
            }
        },

        MYSQL("mysql") {
            @Override
            ConnectionMetaData parse(String connectionUrl, String user) {
                return ConnectionUrlParser.defaultParse(connectionUrl, dbVendor, 3306, user);
            }
        },

        DB2("db2") {
            @Override
            ConnectionMetaData parse(String connectionUrl, String user) {
                return ConnectionUrlParser.defaultParse(connectionUrl, dbVendor, 50000, user);
            }
        },

        H2("h2") {
            // Actually behaves like the default, but better have it explicit
            @Override
            ConnectionMetaData parse(String connectionUrl, String user) {
                return ConnectionUrlParser.defaultParse(connectionUrl, dbVendor, -1, user);
            }
        },

        DERBY("derby") {
            @Override
            ConnectionMetaData parse(String connectionUrl, String user) {
                return ConnectionUrlParser.defaultParse(connectionUrl, dbVendor, 1527, user);
            }
        },

        HSQLDB("hsqldb") {
            @Override
            ConnectionMetaData parse(String connectionUrl, String user) {
                return ConnectionUrlParser.defaultParse(connectionUrl, dbVendor, 9001, user);
            }
        },

        MARIADB("mariadb") {
            @Override
            ConnectionMetaData parse(String connectionUrl, String user) {
                // just like MySQL
                String host = "localhost";
                int port = 3306;
                HostPort hostPort = parseHostPort(connectionUrl);
                if (hostPort.host == null) {
                    // but may also allow a non-proper URL format, like jdbc:mariadb:myhost:666
                    hostPort = parseAuthority(connectionUrl);
                }
                if (hostPort.host != null) {
                    host = hostPort.host;
                    if (hostPort.port > 0) {
                        port = hostPort.port;
                    }
                }
                return new ConnectionMetaData(dbVendor, host, port, user);
            }
        },

        SQLSERVER("sqlserver") {
            @Override
            ConnectionMetaData parse(String connectionUrl, String user) {
                // just like MySQL
                String host = "localhost";
                int port = 1433;
                int indexOfProperties = connectionUrl.indexOf(';');
                if (indexOfProperties > 0) {
                    if (connectionUrl.length() > indexOfProperties + 1) {
                        String propertiesPart = connectionUrl.substring(indexOfProperties + 1);
                        String[] properties = propertiesPart.split(";");
                        for (String property : properties) {
                            String[] parts = property.split("=");
                            if (parts.length == 2 && parts[0].equals("serverName")) {
                                host = parts[1];
                            }
                        }
                    }
                    connectionUrl = connectionUrl.substring(0, indexOfProperties);
                }
                HostPort hostPort = parseHostPort(connectionUrl);

                if (hostPort.host != null) {
                    host = hostPort.host;
                    if (hostPort.port > 0) {
                        port = hostPort.port;
                    }
                }

                // remove the instance part of the host
                int indexOfInstance = host.indexOf('\\');
                if (indexOfInstance > 0) {
                    host = host.substring(0, indexOfInstance);
                }
                return new ConnectionMetaData(dbVendor, host, port, user);
            }
        },

        UNKNOWN("unknown") {
            @Override
            ConnectionMetaData parse(String connectionUrl, String user) {
                return new ConnectionMetaData(dbVendor, null, -1, user);
            }
        };

        ConnectionUrlParser(String dbVendor) {
            this.dbVendor = dbVendor;
        }

        final String dbVendor;

        abstract ConnectionMetaData parse(String connectionUrl, String user);

        static ConnectionMetaData defaultParse(String connectionUrl, String dbVendor, int defaultPort, String user) {
            // Examples:
            // database
            // /
            // //host:666/database
            // //host/database
            // //host:666/
            // //host/
            // //host:666/database?prop1=val1&prop2=val2
            // //host:666/database;prop1=val1;prop2=val2

            // try remove properties appended with semicolon
            int indexOfProperties = connectionUrl.indexOf(';');
            if (indexOfProperties > 0) {
                connectionUrl = connectionUrl.substring(0, indexOfProperties);
            }

            String host = "localhost";
            int port = -1;
            HostPort hostPort = parseHostPort(connectionUrl);
            if (hostPort.host != null) {
                host = hostPort.host;
                if (hostPort.port > 0) {
                    port = hostPort.port;
                } else {
                    port = defaultPort;
                }
            }
            return new ConnectionMetaData(dbVendor, host, port, user);
        }

        /**
         * Expects a URL structure, from which the authority component is extracted to get host and port.
         *
         * @param url expected structure: "[...//]host[:port][/[instance/database]]
         * @return extracted host and port
         */
        static HostPort parseHostPort(String url) {
            if (url.length() > 0) {
                int indexOfDoubleSlash = url.indexOf("//");
                if (indexOfDoubleSlash >= 0 && url.length() > indexOfDoubleSlash + 2) {
                    url = url.substring(indexOfDoubleSlash + 2);
                    if (url.length() == 1) {
                        // for urls such as: jdbc:mysql:///
                        return new HostPort("localhost", -1);
                    }
                    return parseAuthority(url);
                }
            }
            return new HostPort(null, -1);
        }

        static HostPort parseAuthority(String url) {
            // Examples:
            // myhost:666/myinstance
            // myhost:666/myinstance?arg1=val1&arg2=val2
            // myhost/instance
            // myhost/instance?arg1=val1&arg2=val2
            // myhost:666
            // myhost:666?arg1=val1&arg2=val2
            // myhost
            // myhost?arg1=val1&arg2=val2
            int indexOrProperties = url.indexOf('?');
            if (indexOrProperties > 0) {
                url = url.substring(0, indexOrProperties);
            }

            String hostPort;
            int indexOfSlash = url.indexOf('/');
            if (indexOfSlash > 0) {
                hostPort = url.substring(0, indexOfSlash);
            } else {
                hostPort = url;
            }

            String host;
            int port = -1;
            int indexOfColon = hostPort.indexOf(':');
            if (indexOfColon > 0) {
                // check if IPv6
                int lastIndexOfColon = hostPort.lastIndexOf(':');
                if (indexOfColon != lastIndexOfColon) {
                    // IPv6 - [::1] or ::1 or [::1]:666
                    int indexOfIpv6End = hostPort.indexOf(']');
                    if (indexOfIpv6End > 0 && hostPort.length() > indexOfIpv6End + 1) {
                        indexOfColon = indexOfIpv6End + 1;
                    } else {
                        // no port specified
                        indexOfColon = -1;
                    }
                }
            }

            if (indexOfColon > 0) {
                host = hostPort.substring(0, indexOfColon);
                if (hostPort.length() > indexOfColon + 1) {
                    port = Integer.parseInt(hostPort.substring(indexOfColon + 1));
                }
            } else {
                host = hostPort;
            }

            return new HostPort(host, port);
        }

        static class HostPort {
            @Nullable
            String host;
            int port;

            public HostPort(@Nullable String host, int port) {
                this.host = host;
                this.port = port;
            }
        }
    }
}
