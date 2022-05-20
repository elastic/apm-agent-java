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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@GlobalState
public class ConnectionMetaData {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionMetaData.class);

    private final String dbVendor;
    @Nullable
    private final String host;
    private final int port;
    @Nullable
    private final String instance;
    @Nullable
    private final String user;

    private ConnectionMetaData(String dbVendor, @Nullable String host, int port, @Nullable String instance, @Nullable String user) {
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

    @Nullable
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

    // package protected for testing
    enum ConnectionUrlParser {
        ORACLE("oracle") {
            @Override
            Builder parse(String vendorUrl, Builder builder) {
                builder.withPort(1521);

                // Examples:
                // jdbc:oracle:thin:scott/tiger@//myhost:1521/myinstance
                // jdbc:oracle:thin:scott/tiger@127.0.0.1:666:myinstance
                // jdbc:oracle:thin:scott/tiger@localhost:myinstance
                // jdbc:oracle:oci:scott/tiger/@
                // jdbc:oracle:thin:@ldap://ldap.acme.com:7777/sales,cn=OracleContext,dc=com
                // jdbc:oracle:thin:@(DESCRIPTION=(LOAD_BALANCE=on)(ADDRESS=(PROTOCOL=TCP)(HOST=host1)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=service_name)))
                // jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=cluster_alias)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=service_name)))
                // jdbc:oracle:thin:@(DESCRIPTION=(LOAD_BALANCE=on)(ADDRESS_LIST=(ADDRESS=(PROTOCOL=TCP)(HOST=host1)(PORT=1521))(ADDRESS=(PROTOCOL=TCP)(HOST=host2)(PORT=1521)))(CONNECT_DATA=(SERVICE_NAME=service_name)))
                int indexOfUserDetailsEnd = vendorUrl.indexOf('@');
                if (indexOfUserDetailsEnd > 0) {
                    if (vendorUrl.length() > indexOfUserDetailsEnd + 1) {
                        vendorUrl = vendorUrl.substring(indexOfUserDetailsEnd + 1).trim();
                    } else {
                        // jdbc:oracle:oci:scott/tiger/@
                        // nothing left to parse
                        return builder;
                    }
                }

                if (vendorUrl.startsWith("(")) {
                    // (DESCRIPTION=(LOAD_BALANCE=on)(ADDRESS=(PROTOCOL=TCP)(HOST=host1)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=service_name)))
                    // (DESCRIPTION=(LOAD_BALANCE=on)(ADDRESS_LIST=(ADDRESS=(PROTOCOL=TCP)(HOST=host1)(PORT=1521))(ADDRESS=(PROTOCOL=TCP)(HOST=host2)(PORT=1521)))(CONNECT_DATA=(SERVICE_NAME=service_name)))
                    try {
                        TreeNode parsedTree = buildOracleTree(vendorUrl);
                        if (parsedTree == null) {
                            logger.warn("Failed to parse Oracle DB address list from: {}", vendorUrl);
                        } else {
                            traverseOracleTree(vendorUrl, parsedTree, builder);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse oracle description {}", vendorUrl);
                        return builder.withParsingError();
                    }
                } else if (vendorUrl.startsWith("//")) {

                    // try looking for a //host:port/instance pattern
                    String authority = vendorUrl.substring(2);

                    int authorityEnd = authority.indexOf('/');
                    if (authorityEnd >= 0) {
                        authority = authority.substring(0, authorityEnd);
                    }

                    parseAuthority(authority, builder);
                } else {

                    // Thin driver host:port:sid syntax:
                    // myhost:666:instance
                    // myhost:instance
                    // thin:myhost:port:instance
                    if (vendorUrl.startsWith("thin:")) {
                        vendorUrl = vendorUrl.substring("thin:".length());
                    }

                    String[] parts = vendorUrl.split(":");
                    if (parts.length > 0) {
                        builder.withHost(parts[0]);
                    }
                    if (parts.length > 1) {
                        String portOrDb = parts[1];
                        boolean isInt = true;
                        for (char c : portOrDb.toCharArray()) {
                            isInt = isInt && c >= '0' && c <= '9';
                        }
                        if (isInt) {
                            builder.withPort(toNumericPort(vendorUrl, portOrDb));
                        } else {
                            builder.withInstance(portOrDb);
                        }
                    }
                    if (parts.length > 2) {
                        // assume the last item is always the instance name if provided
                        builder.withInstance(parts[2]);
                    }

                }
                return builder;
            }

            @Nullable
            private TreeNode buildOracleTree(String connectionUrl) {
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
                return parsedTree;
            }

            private void traverseOracleTree(String connectionUrl, TreeNode treeNode, Builder builder) {
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
                    if (host != null && !builder.hasHost()) { // first value wins
                        builder.withHost(host).withPort(port);
                    }
                }

                for (TreeNode childNode : treeNode.childNodes) {
                    traverseOracleTree(connectionUrl, childNode, builder);
                }
            }

            class TreeNode {
                final StringBuilder name = new StringBuilder();
                final StringBuilder value = new StringBuilder();
                final List<TreeNode> childNodes = new ArrayList<>();
            }
        },

        POSTGRESQL("postgresql") {
            @Override
            Builder parse(String vendorUrl, Builder builder) {
                return defaultParse(vendorUrl, builder.withHostLocalhost().withPort(5432));
            }
        },

        MYSQL("mysql") {
            @Override
            Builder parse(String vendorUrl, Builder builder) {
                builder.withHostLocalhost()
                    .withPort(3306);
                parseMySqlFlavor(vendorUrl, builder);
                return builder;
            }
        },

        DB2("db2") {
            @Override
            Builder parse(String vendorUrl, Builder builder) {
                return defaultParse(vendorUrl, builder.withPort(50000));
            }

            @Override
            protected String defaultParseInstance(String afterAuthority) {
                // jdbc:db2://myhost/mydb:user=dbadm;
                int separator = afterAuthority.indexOf(':');
                if (separator > 0) {
                    return afterAuthority.substring(0, separator);
                }
                return super.defaultParseInstance(afterAuthority);
            }
        },

        H2("h2") {
            private final String MEM_SUBPROTOCOL = "mem:";
            private final Set<String> LOCAL_SUBPROTOCOLS = new HashSet<>(Arrays.asList("file:", MEM_SUBPROTOCOL));

            @Override
            Builder parse(String vendorUrl, Builder builder) {
                String localInstance = null;
                for (String subprotocol : LOCAL_SUBPROTOCOLS) {
                    if (vendorUrl.startsWith(subprotocol)) {
                        localInstance = vendorUrl.substring(subprotocol.length());
                    }
                }
                if (localInstance != null) {
                    return builder
                        .withLocalAccess()
                        .withInstance(localInstance);
                }

                return defaultParse(vendorUrl, builder.withHostLocalhost());
            }

            @Override
            protected String defaultParseInstance(String afterAuthority) {
                if (afterAuthority.startsWith(MEM_SUBPROTOCOL)) {
                    return afterAuthority.substring(MEM_SUBPROTOCOL.length());
                }
                return super.defaultParseInstance(afterAuthority);
            }
        },

        DERBY("derby") {
            private final String MEMORY_SUBPROTOCOL = "memory:";
            private final Set<String> LOCAL_SUBPROTOCOLS = new HashSet<>(Arrays.asList(MEMORY_SUBPROTOCOL, "jar:", "classpath:","directory:"));

            @Override
            Builder parse(String vendorUrl, Builder builder) {
                boolean isLocal = !vendorUrl.contains(":");
                String localInstance = null;
                for (String subProtocol : LOCAL_SUBPROTOCOLS) { // TODO : maybe move this behavior to common default
                    if(vendorUrl.startsWith(subProtocol)){
                        isLocal = true;
                        // for local sub-protocols everything after sub-protocol but before properties is part of instance
                        localInstance = vendorUrl.substring(subProtocol.length());
                        int propertiesStart = localInstance.indexOf(';');
                        if (propertiesStart > 0) {
                            localInstance = localInstance.substring(0, propertiesStart);
                        }
                    }
                }

                if (isLocal) {
                    builder.withLocalAccess();
                    builder.withInstance(localInstance != null ? localInstance : vendorUrl);
                    return builder;
                } else {
                    builder = builder.withPort(1527);
                    return defaultParse(vendorUrl, builder);
                }
            }

            @Override
            protected String defaultParseInstance(String afterAuthority) {
                if (afterAuthority.startsWith(MEMORY_SUBPROTOCOL)) {
                    return afterAuthority.substring(MEMORY_SUBPROTOCOL.length());
                }
                return super.defaultParseInstance(afterAuthority);
            }
        },

        HSQLDB("hsqldb") {
            @Override
            Builder parse(String vendorUrl, Builder builder) {
                if (vendorUrl.startsWith("file:") || vendorUrl.startsWith("mem:")) {
                    builder = builder.withLocalAccess();
                } else {
                    builder = builder.withPort(9001);
                }
                return defaultParse(vendorUrl, builder);
            }
        },

        MARIADB("mariadb") {

            final List<String> SPECIALIZED_PROTOCOL_STRINGS = new ArrayList<>(Arrays.asList(
                // https://mariadb.com/kb/en/failover-and-high-availability-with-mariadb-connector-j/#configuration
                "sequential:", "loadbalance:", "failover:", "replication:", "aurora:"
            ));

            @Override
            Builder parse(String vendorUrl, Builder builder) {
                for (String protocol : SPECIALIZED_PROTOCOL_STRINGS) {
                    int indexOfProtocol = vendorUrl.indexOf(protocol);
                    if (indexOfProtocol >= 0) {
                        vendorUrl = vendorUrl.substring(indexOfProtocol + protocol.length());
                    }
                }
                if (!vendorUrl.contains("//")) {
                    vendorUrl = "//" + vendorUrl;
                }

                builder.withHostLocalhost()
                    .withPort(3306);
                parseMySqlFlavor(vendorUrl, builder);
                return builder;
            }

        },

        SQLSERVER("sqlserver") {
            @Override
            Builder parse(String vendorUrl, Builder builder) {
                builder.withVendor("mssql")
                    .withHostLocalhost()
                    .withPort(1433);

                String authority = null;
                String dbName = null;

                int indexOfProperties = vendorUrl.indexOf(';');
                if (indexOfProperties < 0) {
                    authority = vendorUrl.substring(2);
                } else {
                    if (vendorUrl.length() > indexOfProperties + 1) {
                        String propertiesPart = vendorUrl.substring(indexOfProperties + 1);
                        String[] properties = propertiesPart.split(";");
                        for (String property : properties) {
                            String[] parts = property.split("=");
                            if (parts.length == 2 && parts[0].equals("serverName")) {
                                authority = parts[1];
                            }
                        }
                    }
                    if (authority == null) {
                        authority = vendorUrl.substring(2, indexOfProperties);
                    }
                }

                int backSlashIndex = authority.indexOf('\\');
                if (backSlashIndex > 0) {
                    // authority can be in multiple formats: 'host\db:777', 'host:777' or 'host\db'
                    dbName = authority.substring(backSlashIndex + 1);
                    int portSeparator = dbName.indexOf(':');
                    String portSuffix = null;
                    if (portSeparator > 0) {
                        portSuffix = dbName.substring(portSeparator);
                        dbName = dbName.substring(0, portSeparator);
                    }

                    authority = authority.substring(0, backSlashIndex);
                    if (portSuffix != null) {
                        authority += portSuffix;
                    }
                }

                parseAuthority(authority, builder);

                builder.withInstance(dbName);

                return builder;
            }
        },

        DEFAULT("default") {
            @Override
            Builder parse(String vendorUrl, Builder builder) {
                return defaultParse(vendorUrl, builder);
            }
        };

        private static final Map<String, ConnectionUrlParser> parsers = new HashMap<>();

        static {
            for (ConnectionUrlParser parser : values()) {
                if (parser != DEFAULT) {
                    parsers.put(parser.dbVendor, parser);
                }
            }
        }

        /**
         * Get JDBC URL parser
         *
         * @param vendor vendor identifier
         * @return vendor-specific URL parser, or default one if no vendor-specific is available
         */
        static ConnectionUrlParser getParser(String vendor) {
            ConnectionUrlParser parser = parsers.get(vendor);
            if (parser == null) {
                parser = DEFAULT;
            }
            return parser;
        }

        ConnectionUrlParser(String dbVendor) {
            this.dbVendor = dbVendor;
        }

        final String dbVendor;

        /**
         * Parses the connection metadata from URL
         *
         * @param vendorUrl vendor-specific part of the url, after the 'jdbc:vendor:' prefix
         * @param builder   builder
         * @return builder
         */
        abstract ConnectionMetaData.Builder parse(String vendorUrl, ConnectionMetaData.Builder builder);

        protected ConnectionMetaData.Builder defaultParse(String vendorUrl, ConnectionMetaData.Builder builder) {
            // Examples:
            // database
            // /
            // //host:666/database
            // //host/database
            // //host:666/
            // //host/
            // //host:666/database?prop1=val1&prop2=val2
            // //host:666/database;prop1=val1;prop2=val2

            int indexOfProperties = Math.max(vendorUrl.indexOf(';'), vendorUrl.indexOf('?'));
            if (indexOfProperties > 0) {
                vendorUrl = vendorUrl.substring(0, indexOfProperties);
            }

            builder.withHostLocalhost();// default to localhost when not known

            int authorityStart = vendorUrl.indexOf("//");
            String afterAuthority = null;
            if (authorityStart >= 0) {
                String authority = vendorUrl.substring(authorityStart + 2);

                if (authority.equals("/")) {
                    // for urls such as: jdbc:hsqldb:///
                    builder.withLocalAccess();
                } else {
                    int authorityEnd = authority.indexOf('/');
                    if (authorityEnd > 0) {
                        afterAuthority = authority.substring(authorityEnd + 1);
                        authority = authority.substring(0, authorityEnd);
                    }

                    parseAuthority(authority, builder);
                }
            } else {
                // no authority: assume only db name
                builder.withInstance(vendorUrl);
            }

            if (afterAuthority != null) {
                builder.withInstance(defaultParseInstance(afterAuthority));
            }

            return builder;
        }

        protected String defaultParseInstance(String afterAuthority) {
            // db name is after the last '/' for the most common case, but could be vendor-specific
            int dbNameStart = afterAuthority.lastIndexOf('/');
            return afterAuthority.substring(dbNameStart + 1);
        }

        /**
         * Parses the authority part of JDBC URL
         *
         * @param authority authority string in the 'host' or 'host:666' format
         * @param builder   builder
         */
        static void parseAuthority(String authority, Builder builder) {
            if (authority.isEmpty()) {
                return;
            }

            String host;
            int port = -1;
            int indexOfColon = authority.indexOf(':');
            if (indexOfColon > 0) {
                // check if IPv6
                int lastIndexOfColon = authority.lastIndexOf(':');
                if (indexOfColon != lastIndexOfColon) {
                    // IPv6 - [::1] or ::1 or [::1]:666
                    int indexOfIpv6End = authority.indexOf(']');
                    if (indexOfIpv6End > 0 && authority.length() > indexOfIpv6End + 1) {
                        indexOfColon = indexOfIpv6End + 1;
                    } else {
                        // no port specified
                        indexOfColon = -1;
                    }
                }
            }

            if (indexOfColon > 0) {
                host = authority.substring(0, indexOfColon);
                if (authority.length() > indexOfColon + 1) {
                    port = toNumericPort(authority, authority.substring(indexOfColon + 1));
                }
            } else {
                host = authority;
            }

            builder.withHost(host).withPort(port);
        }

        static int toNumericPort(String url, String portString) {
            int port = -1;
            try {
                port = Integer.parseInt(portString);
            } catch (NumberFormatException e) {
                logger.debug("Port parsed from the connection string {} is not a number - {}", url, portString);
            }
            return port;
        }


        /**
         * Parses MySQL connection URL
         *
         * @param vendorUrl vendor URL, everything after the 'jdbc:mysql:' prefix.
         * @param builder   builder
         */
        static void parseMySqlFlavor(String vendorUrl, Builder builder) {
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

            vendorUrl = vendorUrl.toLowerCase().trim();
            final Pattern pattern = Pattern.compile("//([^/?]+)");
            Matcher matcher = pattern.matcher(vendorUrl);
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
                            port = toNumericPort(vendorUrl, portMatcher.group(1).trim());
                        }
                        builder.withHost(host).withPort(port);
                        return;
                    } else {
                        logger.warn("Failed to parse address from a connection URL: {}", vendorUrl);
                        builder.withParsingError();
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
                                port = toNumericPort(vendorUrl, keyValue[1].trim());
                            }
                        }
                    }
                    if (host != null) {
                        builder.withHost(host).withPort(port);
                        return;
                    } else {
                        logger.warn("Failed to parse address from a connection URL: {}", vendorUrl);
                        builder.withParsingError();
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
                        return;
                    }
                }

                parseAuthority(firstHost.trim(), builder);
            }

        }
    }

    public static Builder parse(String url) {

        String vendor = "unknown";

        // trimming a temp copy, keeping the original for logging purposes
        String vendorUrl = url;

        // Connection URLs have a common prefix, starting with "jdbc:", followed by the vendor name and a colon.
        // The rest is vendor specific.
        //
        // Examples:
        // jdbc:postgresql://hostname/db?user=jdo&password=pass
        // jdbc:sqlserver://localhost:32958;sslProtocol=TLS;jaasConfigurationName=SQLJDBCDriver
        // jdbc:oracle:oci:root/secret@localhost:1521:testdb
        // jdbc:derby:memory:testdb
        // jdbc:h2:mem:test
        int indexOfJdbc = vendorUrl.indexOf("jdbc:");

        if (indexOfJdbc != -1) {
            vendorUrl = vendorUrl.substring(indexOfJdbc + 5);
            int indexOfNextColon = vendorUrl.indexOf(":");
            if (indexOfNextColon != -1) {
                vendor = vendorUrl.substring(0, indexOfNextColon);
                vendorUrl = vendorUrl.substring(indexOfNextColon + 1);
            }
        }

        // Further parsing needs to be vendor specific.
        ConnectionUrlParser connectionUrlParser = ConnectionUrlParser.getParser(vendor);

        ConnectionMetaData.Builder builder = new Builder(vendor);
        try {
            builder = connectionUrlParser.parse(vendorUrl, builder);
        } catch (Exception e) {
            logger.error(String.format("Failed to parse connection URL: %s with parser %s", url, connectionUrlParser), e);
        }

        return builder;
    }

    public static class Builder {

        private static final String LOCALHOST = "localhost";

        @Nullable
        private String user;
        @Nullable
        private String instance;

        private String vendor;

        @Nullable
        private String host;

        private int port = -1;

        private Builder(String vendor) {
            this.vendor = vendor;
        }

        public Builder withVendor(String vendor) {
            this.vendor = vendor;
            return this;
        }

        public Builder withConnectionUser(@Nullable String user) {
            if (this.user == null) {
                this.user = user;
            }
            return this;
        }

        public Builder withUser(@Nullable String user) {
            this.user = user;
            return this;
        }

        public Builder withInstance(@Nullable String instance) {
            if (instance != null && instance.length() > 0) {
                this.instance = instance;
            }
            return this;
        }

        public Builder withHost(@Nullable String host) {
            if (host != null) {
                this.host = host;
            }
            return this;
        }

        public Builder withHostLocalhost() {
            this.host = LOCALHOST;
            return this;
        }

        public Builder withPort(int port) {
            if (port > 0) {
                this.port = port;
            }
            return this;
        }

        /**
         * Sets the host and port for in-memory of filesystem access
         *
         * @return this
         */
        public Builder withLocalAccess() {
            this.host = LOCALHOST;
            this.port = -1;
            return this;
        }

        public Builder withConnectionInstance(@Nullable String instance) {
            if (this.instance == null) {
                this.instance = instance;
            }
            return this;
        }

        public ConnectionMetaData build() {
            return new ConnectionMetaData(vendor, host, port, instance, user);
        }

        public Builder withParsingError() {
            host = null;
            port = -1;
            instance = null;
            return this;
        }

        /**
         * @return {@literal true if host is already set}
         */
        public boolean hasHost() {
            return host != null;
        }
    }
}
