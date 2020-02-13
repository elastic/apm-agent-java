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

import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionMetaDataTest {

    @Test
    void testOracle() {
        // https://docs.oracle.com/cd/B28359_01/java.111/b31224/urls.htm#BEIJFHHB
        testUrl("jdbc:oracle:thin:scott/tiger@//myhost:666/myinstance", "oracle", "myhost", 666);
        testUrl("jdbc:oracle:thin:scott/tiger@//myhost/myinstance", "oracle", "myhost", 1521);
        testUrl("jdbc:oracle:thin:scott/tiger@//myhost:666", "oracle", "myhost", 666);
        testUrl("jdbc:oracle:thin:scott/tiger@//myhost", "oracle", "myhost", 1521);
        testUrl("jdbc:oracle:thin:scott/tiger@myhost:666:myinstance", "oracle", "myhost", 666);
        testUrl("jdbc:oracle:thin:scott/tiger@myhost:myinstance", "oracle", "myhost", 1521);
        testUrl("jdbc:oracle:thin:scott/tiger@myhost:666", "oracle", "myhost", 666);
        testUrl("jdbc:oracle:thin:scott/tiger@myhost", "oracle", "myhost", 1521);
        testUrl("jdbc:oracle:thin:scott/tiger@", "oracle", null, 1521);
        testUrl("jdbc:oracle:thin:myhost:666:myinstance", "oracle", "myhost", 666);
        testUrl("jdbc:oracle:thin:myhost:myinstance", "oracle", "myhost", 1521);
        testUrl("jdbc:oracle:thin:myhost:666", "oracle", "myhost", 666);
        testUrl("jdbc:oracle:thin:myhost", "oracle", "myhost", 1521);
    }

    @Test
    void testPostgresql() {
        // https://jdbc.postgresql.org/documentation/head/connect.html
        testUrl("jdbc:postgresql://myhost:666/database", "postgresql", "myhost", 666);
        testUrl("jdbc:postgresql://myhost/database", "postgresql", "myhost", 5432);
        testUrl("jdbc:postgresql://myhost:666/", "postgresql", "myhost", 666);
        testUrl("jdbc:postgresql://myhost/", "postgresql", "myhost", 5432);
        testUrl("jdbc:postgresql://127.0.0.1/", "postgresql", "127.0.0.1", 5432);
        testUrl("jdbc:postgresql://::1/", "postgresql", "::1", 5432);
        testUrl("jdbc:postgresql://[::1]/", "postgresql", "[::1]", 5432);
        testUrl("jdbc:postgresql://[::1]:666/", "postgresql", "[::1]", 666);

        testUrl("jdbc:postgresql:database", "postgresql", "localhost", -1);
        testUrl("jdbc:postgresql:/", "postgresql", "localhost", -1);
    }

    @Test
    void testMysql() {
        // https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-jdbc-url-format.html
        // Single host:
        testUrl("jdbc:mysql://myhost:666/database", "mysql", "myhost", 666);
        testUrl("jdbc:mysql://myhost/database", "mysql", "myhost", 3306);
        testUrl("jdbc:mysql://myhost:666/", "mysql", "myhost", 666);
        testUrl("jdbc:mysql://myhost/", "mysql", "myhost", 3306);
        testUrl("jdbc:mysql://127.0.0.1/", "mysql", "127.0.0.1", 3306);
        testUrl("jdbc:mysql://::1/", "mysql", "::1", 3306);
        testUrl("jdbc:mysql://[::1]/", "mysql", "[::1]", 3306);
        testUrl("jdbc:mysql://[::1]:666/", "mysql", "[::1]", 666);

        testUrl("jdbc:mysql://myhost", "mysql", "myhost", 3306);
        testUrl("jdbc:mysql://myhost:666/database?prop1=val1&prop2=val2", "mysql", "myhost", 666);
        testUrl("jdbc:mysql://myhost:666?prop1=val1&prop2=val2", "mysql", "myhost", 666);
    }

    @Test
    void testMariadb() {
        // https://mariadb.com/kb/en/about-mariadb-connector-j/#connection-strings
        // Just like MySQL, but although not documented, seems to also allow the form: jdbc:mariadb:myhost:666/database
        testUrl("jdbc:mariadb://myhost:666/database", "mariadb", "myhost", 666);
        testUrl("jdbc:mariadb://myhost/database", "mariadb", "myhost", 3306);
        testUrl("jdbc:mariadb://myhost:666/", "mariadb", "myhost", 666);
        testUrl("jdbc:mariadb://myhost/", "mariadb", "myhost", 3306);
        testUrl("jdbc:mariadb://127.0.0.1/", "mariadb", "127.0.0.1", 3306);
        testUrl("jdbc:mariadb://::1/", "mariadb", "::1", 3306);
        testUrl("jdbc:mariadb://[::1]/", "mariadb", "[::1]", 3306);
        testUrl("jdbc:mariadb://[::1]:666/", "mariadb", "[::1]", 666);

        testUrl("jdbc:mariadb://myhost", "mariadb", "myhost", 3306);
        testUrl("jdbc:mariadb://myhost:666/database?prop1=val1&prop2=val2", "mariadb", "myhost", 666);
        testUrl("jdbc:mariadb://myhost:666?prop1=val1&prop2=val2", "mariadb", "myhost", 666);

        testUrl("jdbc:mariadb:myhost:666/database", "mariadb", "myhost", 666);
        testUrl("jdbc:mariadb:myhost:666/database?prop1=val1&prop2=val2", "mariadb", "myhost", 666);
        testUrl("jdbc:mariadb:myhost/database", "mariadb", "myhost", 3306);
        testUrl("jdbc:mariadb:myhost/database?prop1=val1&prop2=val2", "mariadb", "myhost", 3306);
        testUrl("jdbc:mariadb:myhost:666", "mariadb", "myhost", 666);
        testUrl("jdbc:mariadb:myhost:666?prop1=val1&prop2=val2", "mariadb", "myhost", 666);
        testUrl("jdbc:mariadb:myhost", "mariadb", "myhost", 3306);
        testUrl("jdbc:mariadb:myhost?prop1=val1&prop2=val2", "mariadb", "myhost", 3306);
    }

    @Test
    void testSqlserver() {
        // https://docs.microsoft.com/en-us/sql/connect/jdbc/building-the-connection-url?view=sql-server-ver15
        testUrl("jdbc:sqlserver://myhost\\instance:666", "sqlserver", "myhost", 666);
        testUrl("jdbc:sqlserver://myhost\\instance:666;prop1=val1;prop2=val2", "sqlserver", "myhost", 666);
        testUrl("jdbc:sqlserver://myhost:666", "sqlserver", "myhost", 666);
        testUrl("jdbc:sqlserver://myhost:666;prop1=val1;prop2=val2", "sqlserver", "myhost", 666);
        testUrl("jdbc:sqlserver://myhost\\instance", "sqlserver", "myhost", 1433);
        testUrl("jdbc:sqlserver://myhost\\instance;prop1=val1;prop2=val2", "sqlserver", "myhost", 1433);
        testUrl("jdbc:sqlserver://myhost", "sqlserver", "myhost", 1433);
        testUrl("jdbc:sqlserver://myhost;prop1=val1;prop2=val2", "sqlserver", "myhost", 1433);
        testUrl("jdbc:sqlserver://", "sqlserver", "localhost", 1433);
        testUrl("jdbc:sqlserver://;prop1=val1;prop2=val2", "sqlserver", "localhost", 1433);
        testUrl("jdbc:sqlserver://;", "sqlserver", "localhost", 1433);
        testUrl("jdbc:sqlserver://;serverName=myhost", "sqlserver", "myhost", 1433);
        testUrl("jdbc:sqlserver://;prop1=val1;serverName=myhost", "sqlserver", "myhost", 1433);
        testUrl("jdbc:sqlserver://;serverName=myhost;prop1=val1", "sqlserver", "myhost", 1433);
        testUrl("jdbc:sqlserver://;serverName=myhost\\instance;prop1=val1", "sqlserver", "myhost", 1433);
        testUrl("jdbc:sqlserver://;serverName=3ffe:8311:eeee:f70f:0:5eae:10.203.31.9\\instance;prop1=val1",
            "sqlserver", "3ffe:8311:eeee:f70f:0:5eae:10.203.31.9", 1433);
    }

    @Test
    void testDb2() {
        // https://www.ibm.com/support/knowledgecenter/SSEPGG_11.5.0/com.ibm.db2.luw.apdv.java.doc/src/tpc/imjcc_tjvjcccn.html
        testUrl("jdbc:db2://myhost:666/mydb:user=dbadm;password=dbadm;", "db2", "myhost", 666);
        testUrl("jdbc:db2://[::1]:666/mydb:user=dbadm;password=dbadm;", "db2", "[::1]", 666);
        testUrl("jdbc:db2://127.0.0.1:666/mydb:user=dbadm;password=dbadm;", "db2", "127.0.0.1", 666);
        testUrl("jdbc:db2://myhost/mydb:user=dbadm;password=dbadm;", "db2", "myhost", 50000);
        testUrl("jdbc:db2://myhost;", "db2", "myhost", 50000);
        testUrl("jdbc:db2://my.host;", "db2", "my.host", 50000);
        testUrl("jdbc:db2://myhost", "db2", "myhost", 50000);
    }

    @Test
    void testH2() {
        // http://www.h2database.com/html/features.html#database_url
        testUrl("jdbc:h2:file:/data/sample", "h2", "localhost", -1);
        testUrl("jdbc:h2:mem:", "h2", "localhost", -1);
        testUrl("jdbc:h2:mem:test_mem", "h2", "localhost", -1);
        testUrl("jdbc:h2:tcp://localhost/mem:test", "h2", "localhost", -1);
        testUrl("jdbc:h2:tcp://dbserv:8084/~/sample", "h2", "dbserv", 8084);
        testUrl("jdbc:h2:ssl://dbserv:8085/~/sample;", "h2", "dbserv", 8085);
        testUrl("jdbc:h2:ssl://dbserv:8085/~/sample;prop1=val1;prop2=val2", "h2", "dbserv", 8085);
    }

    @Test
    void testUnknown() {
        testUrl("jdbc:arbitrary://myhost:666/mydb;user=dbadm;password=dbadm;", "arbitrary", "myhost", 666);
        testUrl("jdbc:arbitrary://[::1]:666/mydb?user=dbadm&password=dbadm;", "arbitrary", "[::1]", 666);
        testUrl("jdbc:arbitrary://127.0.0.1:666/mydb;user=dbadm;password=dbadm;", "arbitrary", "127.0.0.1", 666);
        testUrl("jdbc:arbitrary://myhost/mydb;user=dbadm;password=dbadm;", "arbitrary", "myhost", -1);
        testUrl("jdbc:arbitrary://myhost;", "arbitrary", "myhost", -1);
        testUrl("jdbc:arbitrary://my.host;", "arbitrary", "my.host", -1);
        testUrl("jdbc:arbitrary://myhost", "arbitrary", "myhost", -1);
    }

    @Test
    void testDerby() {
        testUrl("jdbc:derby://my.host/memory:mydb;prop1=val1;prop2=val2", "derby", "my.host", 1527);
        testUrl("jdbc:derby://my.host/memory:mydb;prop1=val1;prop2=val2", "derby", "my.host", 1527);
        testUrl("jdbc:derby://my.host:666/memory:mydb;prop1=val1;prop2=val2", "derby", "my.host", 666);
        testUrl("jdbc:derby:jar:/mydb;prop1=val1;prop2=val2", "derby", "localhost", -1);
        testUrl("jjdbc:derby:memory:mydb", "derby", "localhost", -1);
        testUrl("jjdbc:derby:mydb", "derby", "localhost", -1);
    }

    @Test
    void testHsqldb() {
        // http://hsqldb.org/doc/2.0/guide/dbproperties-chapt.html
        testUrl("jdbc:hsqldb:file:~/mydb", "hsqldb", "localhost", -1);
        testUrl("jdbc:hsqldb:file:enrolments;user=aUserName;ifexists=true", "hsqldb", "localhost", -1);
        testUrl("jdbc:hsqldb:hsql://localhost/enrolments;close_result=true", "hsqldb", "localhost", 9001);
        testUrl("jdbc:hsqldb:hsql://my.host/enrolments;close_result=true", "hsqldb", "my.host", 9001);
        testUrl("jdbc:hsqldb:http://192.0.0.10:9500", "hsqldb", "192.0.0.10", 9500);
        testUrl("jdbc:hsqldb:http://dbserver.somedomain.com", "hsqldb", "dbserver.somedomain.com", 9001);
        testUrl("jdbc:hsqldb:mem:", "hsqldb", "localhost", -1);
    }

    @Test
    void testInvalid() {
        testUrl("postgresql://myhost:666/database", "unknown", null, -1);
    }

    private void testUrl(String url, String expectedVendor, @Nullable String expectedHost, int expectedPort) {
        ConnectionMetaData metadata = ConnectionMetaData.create(url, "TEST_USER");
        assertEquals(metadata.getDbVendor(), expectedVendor);
        assertEquals(metadata.getHost(), expectedHost);
        assertEquals(metadata.getPort(), expectedPort);
    }
}
