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

import co.elastic.apm.agent.sdk.state.GlobalState;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@GlobalState
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
     * @param instance      instance identifier from connection
     * @param user          DB user
     * @return metadata of a JDBC connection
     */
    public static ConnectionMetaData create(String connectionUrl, @Nullable String instance, String user) {
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
                ret = connectionUrlParser.parse(tmpUrl, instance, user);
            } catch (Exception e) {
                logger.error("Failed to parse connection URL: " + tmpUrl, e);
            }
        } else {
            // Doesn't hurt to try...
            ret = ConnectionUrlParser.defaultParse(connectionUrl, dbVendor, -1, instance, user);
        }

        if (ret == null) {
            ret = new ConnectionMetaData(dbVendor, null, -1, instance, user);
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
    @Nullable
    private final String instance;
    private final String user;

    private ConnectionMetaData(String dbVendor, @Nullable String host, int port, @Nullable String instance, String user) {
        this.dbVendor = dbVendor;
        this.host = host;
        this.port = port;
        this.instance = instance;
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

    @Nullable
    public String getInstance() {
        return instance;
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
            ", instance='" + instance + '\'' +
            ", user='" + user + '\'' +
            '}';
    }

    @SuppressWarnings("unused")
    private enum ConnectionUrlParser {
        ORACLE("oracle") {
            public static final int DEFAULT_PORT = 1521;

            @Override
            ConnectionMetaData parse(String connectionUrl, String instance, String user) {
                // Examples:
                // jdbc:oracle:thin:scott/tiger@//myhost:1521/myinstance
                // jdbc:oracle:thin:scott/tiger@127.0.0.1:666:myinstance
                // jdbc:oracle:thin:scott/tiger@localhost:myinstance
                // jdbc:oracle:oci:scott/tiger/@
                // jdbc:oracle:thin:@ldap://ldap.acme.com:7777/sales,cn=OracleContext,dc=com
                // jdbc:oracle:thin:@(DESCRIPTION=(LOAD_BALANCE=on)(ADDRESS=(PROTOCOL=TCP)(HOST=host1)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=service_name)))
                // jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=cluster_alias)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=service_name)))
                // jdbc:oracle:thin:@(DESCRIPTION=(LOAD_BALANCE=on)(ADDRESS_LIST=(ADDRESS=(PROTOCOL=TCP)(HOST=host1)(PORT=1521))(ADDRESS=(PROTOCOL=TCP)(HOST=host2)(PORT=1521)))(CONNECT_DATA=(SERVICE_NAME=service_name)))
                int indexOfUserDetailsEnd = connectionUrl.indexOf('@');
                if (indexOfUserDetailsEnd > 0) {
                    if (connectionUrl.length() > indexOfUserDetailsEnd + 1) {
                        connectionUrl = connectionUrl.substring(indexOfUserDetailsEnd + 1).trim();
                    } else {
                        // jdbc:oracle:oci:scott/tiger/@
                        return new ConnectionMetaData(dbVendor, null, DEFAULT_PORT, instance, user);
                    }
                }

                String host = null;
                int port = DEFAULT_PORT;

                HostPort hostPort;
                if (connectionUrl.startsWith("(")) {
                    // (DESCRIPTION=(LOAD_BALANCE=on)(ADDRESS=(PROTOCOL=TCP)(HOST=host1)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=service_name)))
                    // (DESCRIPTION=(LOAD_BALANCE=on)(ADDRESS_LIST=(ADDRESS=(PROTOCOL=TCP)(HOST=host1)(PORT=1521))(ADDRESS=(PROTOCOL=TCP)(HOST=host2)(PORT=1521)))(CONNECT_DATA=(SERVICE_NAME=service_name)))
                    try {
                        hostPort = parseAddressList(connectionUrl);
                        if (hostPort != null) {
                            host = hostPort.host;
                            if (hostPort.port > 0) {
                                port = hostPort.port;
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse address from this address list: {}", connectionUrl);
                        port = -1;
                    }
                } else {
                    // try looking for a //host:port/instance pattern
                    hostPort = parseHostPort(connectionUrl);
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
                            port = toNumericPort(connectionUrl, parts[1], DEFAULT_PORT);
                        }
                    }
                }

                return new ConnectionMetaData(dbVendor, host, port, instance, user);
            }

            @Nullable
            private HostPort parseAddressList(String connectionUrl) {
                TreeNode parsedTree = null;
                Deque<TreeNode> stack = new ArrayDeque<>();
                StringBuilder currentValueBuffer = null;
                for (char c : connectionUrl.toLowerCase().toCharArray()) {
                    switch (c) {
                        case '(': {
                            TreeNode treeNode = new TreeNode();
                            if (stack.isEmpty()) {
                                parsedTree = treeNode;
                            } else {
                                stack.peek().childNodes.add(treeNode);
                            }
                            stack.push(treeNode);
                            currentValueBuffer = treeNode.name;
                            break;
                        }
                        case ')': {
                            stack.pop();
                            // continue to do the same as finding `=`
                        }
                        case '=': {
                            if (stack.isEmpty()) {
                                currentValueBuffer = null;
                            } else {
                                currentValueBuffer = stack.peek().value;
                            }
                            break;
                        }
                        default: {
                            if (currentValueBuffer == null) {
                                logger.warn("Failed to parse Oracle DB address list from: {}", connectionUrl);
                            } else {
                                currentValueBuffer.append(c);
                            }
                        }
                    }
                }

                HostPort ret = null;
                if (parsedTree == null) {
                    logger.warn("Failed to parse Oracle DB address list from: {}", connectionUrl);
                } else {
                    ret = findAddressInTree(connectionUrl, parsedTree);
                }
                return ret;
            }

            @Nullable
            HostPort findAddressInTree(String connectionUrl, TreeNode treeNode) {
                if (treeNode.name.toString().trim().equals("address")) {
                    String host = null;
                    int port = -1;
                    for (TreeNode childNode : treeNode.childNodes) {
                        String name = childNode.name.toString().trim();
                        if (name.equals("host")) {
                            host = childNode.value.toString().trim();
                        } else if (name.equals("port")) {
                            port = toNumericPort(connectionUrl, childNode.value.toString().trim());
                        }
                    }
                    if (host != null) {
                        return new HostPort(host, port);
                    }
                }

                HostPort ret = null;
                for (TreeNode childNode : treeNode.childNodes) {
                    ret = findAddressInTree(connectionUrl, childNode);
                    if (ret != null) {
                        break;
                    }
                }
                return ret;
            }

            class TreeNode {
                StringBuilder name = new StringBuilder();
                StringBuilder value = new StringBuilder();
                List<TreeNode> childNodes = new ArrayList<>();
            }
        },

        POSTGRESQL("postgresql") {
            @Override
            ConnectionMetaData parse(String connectionUrl, String instance, String user) {
                return ConnectionUrlParser.defaultParse(connectionUrl, dbVendor, 5432, instance, user);
            }
        },

        MYSQL("mysql") {
            @Override
            ConnectionMetaData parse(String connectionUrl, String instance, String user) {
                String host = "localhost";
                int port = 3306;
                HostPort hostPort = parseMySqlFlavor(connectionUrl);
                if (hostPort != null) {
                    host = hostPort.host;
                    if (hostPort.port > 0) {
                        port = hostPort.port;
                    }
                }
                return new ConnectionMetaData(dbVendor, host, port, instance, user);
            }
        },

        DB2("db2") {
            @Override
            ConnectionMetaData parse(String connectionUrl, String instance, String user) {
                return ConnectionUrlParser.defaultParse(connectionUrl, dbVendor, 50000, instance, user);
            }
        },

        H2("h2") {
            // Actually behaves like the default, but better have it explicit
            @Override
            ConnectionMetaData parse(String connectionUrl, String instance, String user) {
                return ConnectionUrlParser.defaultParse(connectionUrl, dbVendor, -1, instance, user);
            }
        },

        DERBY("derby") {
            @Override
            ConnectionMetaData parse(String connectionUrl, String instance, String user) {
                return ConnectionUrlParser.defaultParse(connectionUrl, dbVendor, 1527, instance, user);
            }
        },

        HSQLDB("hsqldb") {
            @Override
            ConnectionMetaData parse(String connectionUrl, String instance, String user) {
                return ConnectionUrlParser.defaultParse(connectionUrl, dbVendor, 9001, instance, user);
            }
        },

        MARIADB("mariadb") {

            final List<String> SPECIALIZED_PROTOCOL_STRINGS = new ArrayList<>(Arrays.asList(
                // https://mariadb.com/kb/en/failover-and-high-availability-with-mariadb-connector-j/#configuration
                "sequential:", "loadbalance:", "failover:", "replication:", "aurora:"
            ));

            @Override
            ConnectionMetaData parse(String connectionUrl, String instance, String user) {
                // just like MySQL
                String host = "localhost";
                int port = 3306;
                // seems to allow a non-proper URL format, like jdbc:mariadb:myhost:666 or jdbc:mariadb:sequential:host1,host2,host3/testdb
                for (String protocol : SPECIALIZED_PROTOCOL_STRINGS) {
                    int indexOfProtocol = connectionUrl.indexOf(protocol);
                    if (indexOfProtocol >= 0) {
                        connectionUrl = connectionUrl.substring(indexOfProtocol + protocol.length());
                    }
                }
                if (!connectionUrl.contains("//")) {
                    connectionUrl = "//" + connectionUrl;
                }
                HostPort hostPort = parseMySqlFlavor(connectionUrl);
                if (hostPort != null) {
                    host = hostPort.host;
                    if (hostPort.port > 0) {
                        port = hostPort.port;
                    }
                }
                return new ConnectionMetaData(dbVendor, host, port, instance, user);
            }
        },

        SQLSERVER("sqlserver") {
            @Override
            ConnectionMetaData parse(String connectionUrl, String instance, String user) {
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
                return new ConnectionMetaData("mssql", host, port, instance, user);
            }
        },

        UNKNOWN("unknown") {
            @Override
            ConnectionMetaData parse(String connectionUrl, String instance, String user) {
                return new ConnectionMetaData(dbVendor, null, -1, instance, user);
            }
        };

        ConnectionUrlParser(String dbVendor) {
            this.dbVendor = dbVendor;
        }

        final String dbVendor;

        abstract ConnectionMetaData parse(String connectionUrl, String instance, String user);

        static ConnectionMetaData defaultParse(String connectionUrl, String dbVendor, int defaultPort, @Nullable String instance, String user) {
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
            return new ConnectionMetaData(dbVendor, host, port, instance, user);
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
                        // for urls such as: jdbc:hsqldb:///
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
                    port = toNumericPort(url, hostPort.substring(indexOfColon + 1));
                }
            } else {
                host = hostPort;
            }

            return new HostPort(host, port);
        }

        static int toNumericPort(String url, String portString) {
            return toNumericPort(url, portString, -1);
        }

        static int toNumericPort(String url, String portString, int defaultPort) {
            int port = defaultPort;
            try {
                port = Integer.parseInt(portString);
            } catch (NumberFormatException e) {
                logger.debug("Port parsed from the connection string {} is not a number - {}", url, portString);
            }
            return port;
        }

        @Nullable static HostPort parseMySqlFlavor(String connectionUrl) {
            // https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-jdbc-url-format.html
            // General structure:
            // protocol//[hosts][/database][?properties]

            // Single host:
            // jdbc:mysql://host1:33060/sakila
            // jdbc:mysql://host1:33060/sakila?prop=val
            // jdbc:mysql://host1:33060?prop=val
            // jdbc:mysql://address=(host=myhost)(port=1111)(key1=value1)/db
            // jdbc:mysql://(host=myhost,port=1111,key1=value1)/db

            // Multiple hosts:
            // jdbc:mysql://myhost1:1111,myhost2:2222/db
            // jdbc:mysql://address=(host=myhost1)(port=1111)(key1=value1),address=(host=myhost2)(port=2222)(key2=value2)/db
            // jdbc:mysql://(host=myhost1,port=1111,key1=value1),(host=myhost2,port=2222,key2=value2)/db
            // jdbc:mysql://myhost1:1111,(host=myhost2,port=2222,key2=value2)/db
            // jdbc:mysql://sandy:secret@[myhost1:1111,myhost2:2222]/db
            // jdbc:mysql://sandy:secret@[address=(host=myhost1)(port=1111)(key1=value1),address=(host=myhost2)(port=2222)(key2=value2)]/db
            // jdbc:mysql://sandy:secret@[myhost1:1111,address=(host=myhost2)(port=2222)(key2=value2)]/db

            HostPort ret = null;
            connectionUrl = connectionUrl.toLowerCase().trim();
            final Pattern pattern = Pattern.compile("//([^/?]+)");
            Matcher matcher = pattern.matcher(connectionUrl);
            if (matcher.find()) {
                String hostsPart = matcher.group(1);

                // splitting to hosts, watching out from the "key-value" form: (host=myhost1,port=1111,key1=value1)
                List<String> hosts = new ArrayList<>();
                String[] parts = hostsPart.toLowerCase().trim().split(",");
                StringBuilder sb = new StringBuilder();
                for (String part : parts) {
                    if (part.lastIndexOf(')') < part.lastIndexOf('(')) {
                        // we are in the middle of a "key-value" form, we need to concatenate the next part
                        sb.append(part).append(',');
                    } else {
                        boolean isWithinKeyValuePart = sb.length() > 0;
                        sb.append(part);
                        if (isWithinKeyValuePart && !part.contains(")")) {
                            // key-value part not finished yet
                            sb.append(',');
                            continue;
                        }
                        hosts.add(sb.toString());
                        sb.setLength(0);
                    }
                }

                String firstHost = hosts.get(0);

                // the "address-equals" form: address=(host=myhost1)(port=1111)(key1=value1)
                String addressKey = "address=";
                int indexOfAddress = firstHost.indexOf(addressKey);
                if (indexOfAddress >= 0) {
                    String tmp = firstHost.substring(indexOfAddress + addressKey.length());
                    Matcher hostMatcher = Pattern.compile("\\s*host\\s*=\\s*([^)]+)\\s*").matcher(tmp);
                    if (hostMatcher.find()) {
                        String host = hostMatcher.group(1).trim();
                        int port = -1;
                        Matcher portMatcher = Pattern.compile("\\s*port\\s*=\\s*([^)]+)\\s*").matcher(tmp);
                        if (portMatcher.find()) {
                            port = toNumericPort(connectionUrl, portMatcher.group(1).trim());
                        }
                        return new HostPort(host, port);
                    } else {
                        logger.warn("Failed to parse address from a connection URL: {}", connectionUrl);
                        return null;
                    }
                }

                // the "key-value" form: (host=myhost1,port=1111,key1=value1) - address form shouldn't arrive here
                Matcher keyValueMatcher = Pattern.compile("\\(([^)]+)\\)").matcher(firstHost);
                if (keyValueMatcher.find()) {
                    String keyValuePart = keyValueMatcher.group(1).trim();
                    parts = keyValuePart.split(",");
                    String host = null;
                    int port = -1;
                    for (String part : parts) {
                        String[] keyValue = part.split("=");
                        if (keyValue.length == 2) {
                            if (keyValue[0].trim().equals("host")) {
                                host = keyValue[1].trim();
                            } else if (keyValue[0].trim().equals("port")) {
                                port = toNumericPort(connectionUrl, keyValue[1].trim());
                            }
                        }
                    }
                    if (host != null) {
                        return new HostPort(host, port);
                    } else {
                        logger.warn("Failed to parse address from a connection URL: {}", connectionUrl);
                        return null;
                    }
                }

                int indexOfSquareBracket = firstHost.indexOf('[');
                if (indexOfSquareBracket >= 0) {
                    if (!firstHost.contains("]") || firstHost.lastIndexOf('[') != indexOfSquareBracket) {
                        // not IPv6, probably the "sublist" format, trim up to it
                        firstHost = firstHost.substring(indexOfSquareBracket + 1);
                    }
                }

                // trim user part, if set
                int indexOfUserDetailsEnd = firstHost.indexOf('@');
                if (indexOfUserDetailsEnd >= 0) {
                    if (firstHost.length() > indexOfUserDetailsEnd + 1) {
                        firstHost = firstHost.substring(indexOfUserDetailsEnd + 1).trim();
                    } else {
                        return null;
                    }
                }

                ret = parseAuthority(firstHost.trim());
            }
            return ret;
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
