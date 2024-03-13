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

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionMetaDataTest {

    @Test
    void testOracle() {
        // https://docs.oracle.com/cd/B28359_01/java.111/b31224/urls.htm#BEIJFHHB
        testUrl("jdbc:oracle:thin:scott/tiger@//myhost:666/myinstance", "oracle", "myhost", 666, "myinstance");
        testUrl("jdbc:oracle:thin:scott/tiger@//myhost/myinstance", "oracle", "myhost", 1521, "myinstance");
        testUrl("jdbc:oracle:thin:scott/tiger@//myhost:666", "oracle", "myhost", 666, null);
        testUrl("jdbc:oracle:thin:scott/tiger@//myhost", "oracle", "myhost", 1521, null);
        testUrl("jdbc:oracle:thin:scott/tiger@myhost:666:myinstance", "oracle", "myhost", 666, "myinstance");
        testUrl("jdbc:oracle:thin:scott/tiger@myhost:myinstance", "oracle", "myhost", 1521, "myinstance");
        testUrl("jdbc:oracle:thin:scott/tiger@myhost:666", "oracle", "myhost", 666, null);
        testUrl("jdbc:oracle:thin:scott/tiger@myhost", "oracle", "myhost", 1521, null);
        testUrl("jdbc:oracle:thin:scott/tiger@", "oracle", null, 1521, null);
        testUrl("jdbc:oracle:thin:myhost:666:myinstance", "oracle", "myhost", 666, "myinstance");
        testUrl("jdbc:oracle:thin:myhost:myinstance", "oracle", "myhost", 1521, "myinstance");
        testUrl("jdbc:oracle:thin:myhost:666", "oracle", "myhost", 666, null);
        testUrl("jdbc:oracle:thin:myhost", "oracle", "myhost", 1521, null);

        // address list parsing
        testUrl("jdbc:oracle:thin:@(DESCRIPTION=(LOAD_BALANCE=on)" +
            "(ADDRESS=(PROTOCOL=TCP)(HOST=host1)(PORT=666))" +
            "(CONNECT_DATA=(SERVICE_NAME=Service_Name)))", "oracle", "host1", 666, "Service_Name");
        testUrl("jdbc:oracle:thin:@(DESCRIPTION =(LOAD_BALANCE=on )" +
            "( ADDRESS= (PROTOCOL=TCP) ( HOST= host1 ) (  PORT  = 666 ))" +
            "(CONNECT_DATA=(SERVICE_NAME=service_name)))", "oracle", "host1", 666, "service_name");
        testUrl("jdbc:oracle:thin:@(DESCRIPTION=(LOAD_BALANCE=on)" +
            "(ADDRESS=(PROTOCOL=TCP)(HOST=host1))" +
            "(CONNECT_DATA=(SERVICE_NAME=service_name)))", "oracle", "host1", 1521, "service_name");
        testUrl("jdbc:oracle:thin:@(DESCRIPTION=" +
            "(ADDRESS=(PROTOCOL=TCP)(HOST= cluster_alias)(PORT=666))" +
            "(CONNECT_DATA=(SERVICE_NAME=service_name)))", "oracle", "cluster_alias", 666, "service_name");
        testUrl("jdbc:oracle:thin:@(DESCRIPTION=(LOAD_BALANCE=on)" +
                "(ADDRESS_LIST=(ADDRESS=(PROTOCOL=TCP)(HOST=host1))(ADDRESS=(PROTOCOL=TCP)(HOST=host2)(PORT=1521)))" +
                "(CONNECT_DATA=(SERVICE_NAME=service_name)))",
            "oracle", "host1", 1521, "service_name");
        testUrl("jdbc:oracle:thin:@(DESCRIPTION=" +
            "(ADDRESS=(PROTOCOL=TCP)(HOST=localhost)(PORT=6203))" +
            "(CONNECT_DATA=(SERVER=DEDICATED)(SERVICE_NAME=DB.FQDN.ORG.DE)))", "oracle", "localhost", 6203, "DB.FQDN.ORG.DE");
        testUrl("jdbc:oracle:thin:@(description=" +
            "(address=(protocol=tcp)(host=localhost)(port=6203))" +
            "(connect_data=(server=dedicated)(service_name=db.fqdn.org.de)))", "oracle", "localhost", 6203, "db.fqdn.org.de");
        testUrl("jdbc:oracle:thin:@(description=" +
                "(address=(protocol=tcp)(host=localhost)(port=6203))" +
                "(connect_data=(server=dedicated)(sid=db)))", "oracle", "localhost", 6203, "db");

        // intentional parsing error with extra ')' before '(address='
        testUrl("jdbc:oracle:thin:@(description=)(address=(protocol=tcp)(host=localhost)(port=6203))(connect_data=" +
            "(server=dedicated)(service_name=db.fqdn.org.de)))", "oracle", null, -1, null);

        // instance name

        testUrl("jdbc:oracle:thin:@(description=" +
            "(address_list=(address=(protocol=tcp)(port=1521)(host=oraHost)))" +
            "(connect_data=(INSTANCE_NAME=instance_name)))", "oracle", "orahost", 1521, "instance_name");

        // instance name + service name, not sure if actually relevant but instance should have higher priority
        testUrl("jdbc:oracle:thin:@(description=" +
            "(address_list=(address=(protocol=tcp)(port=1521)(host=oraHost)))" +
            "(connect_data=(SERVICE_NAME=service_name)(INSTANCE_NAME=instance_name)))", "oracle", "orahost", 1521, "instance_name");
    }

    @Test
    void testPostgresql() {
        // https://jdbc.postgresql.org/documentation/head/connect.html
        testUrl("jdbc:postgresql://myhost:666/database", "postgresql", "myhost", 666, "database");
        testUrl("jdbc:postgresql://myhost/database", "postgresql", "myhost", 5432, "database");
        testUrl("jdbc:postgresql://myhost:666/", "postgresql", "myhost", 666, null);
        testUrl("jdbc:postgresql://myhost/", "postgresql", "myhost", 5432, null);
        testUrl("jdbc:postgresql://127.0.0.1/", "postgresql", "127.0.0.1", 5432, null);
        testUrl("jdbc:postgresql://::1/", "postgresql", "::1", 5432, null);
        testUrl("jdbc:postgresql://[::1]/", "postgresql", "::1", 5432, null);
        testUrl("jdbc:postgresql://[::1]:666/", "postgresql", "::1", 666, null);

        testUrl("jdbc:postgresql:database", "postgresql", "localhost", 5432, "database");
        testUrl("jdbc:postgresql:/", "postgresql", "localhost", 5432, null);
    }

    @Test
    void testIngress() {
        // https://docs.actian.com/ingres/11.0/index.html#page/QuickStart_Win/5._Connecting_to_Ingres_Using_JDBC.htm
        testUrl("jdbc:ingres://myhost:II7/testdb;UID=userid;PWD=password", "ingres", "myhost", -1, "testdb");
        testUrl("jdbc:ingres://localhost:II7/testdb;UID=userid;PWD=password", "ingres", "localhost", -1, "testdb");
        testUrl("jdbc:ingres://localhost:567/testdb;UID=userid;PWD=password", "ingres", "localhost", 567, "testdb");
    }

    @Test
    void testMysql() {
        // https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-jdbc-url-format.html
        // Single host:
        testUrl("jdbc:mysql://myhost:666/database", "mysql", "myhost", 666, "database");
        testUrl("jdbc:mysql://myhost:666/database?prop=val", "mysql", "myhost", 666, "database");
        testUrl("jdbc:mysql://myhost:666?prop=val", "mysql", "myhost", 666, null);
        testUrl("jdbc:mysql://myhost/database", "mysql", "myhost", 3306, "database");
        testUrl("jdbc:mysql://myhost:666/", "mysql", "myhost", 666, null);
        testUrl("jdbc:mysql://myhost/", "mysql", "myhost", 3306, null);
        testUrl("jdbc:mysql://127.0.0.1/", "mysql", "127.0.0.1", 3306, null);
        testUrl("jdbc:mysql://::1/", "mysql", "::1", 3306, null);
        testUrl("jdbc:mysql://[::1]/", "mysql", "::1", 3306, null);
        testUrl("jdbc:mysql://[::1]:666/", "mysql", "::1", 666, null);
        testUrl("jdbc:mysql://sandy:secret@myhost1:1111/db", "mysql", "myhost1", 1111, "db");
        testUrl("jdbc:mysql://myhost2:2222,sandy:secret@myhost1:1111/db", "mysql", "myhost2", 2222, "db");
        testUrl("jdbc:mysql://myhost", "mysql", "myhost", 3306, null);
        testUrl("jdbc:mysql://myhost:666/database?prop1=val1&prop2=val2", "mysql", "myhost", 666, "database");
        testUrl("jdbc:mysql://myhost:666?prop1=val1&prop2=val2", "mysql", "myhost", 666, null);

        // multiple hosts
        testUrl("jdbc:mysql://myhost1:1111,myhost2:2222/db", "mysql", "myhost1", 1111, "db");
        testUrl("jdbc:mysql://myhost1:1111,myhost2:2222/db?prop=val", "mysql", "myhost1", 1111, "db");
        testUrl("jdbc:mysql://myhost1:1111,myhost2:2222?prop=val", "mysql", "myhost1", 1111, null);
        testUrl("jdbc:mysql://[2001:0660:7401:0200:0000:0000:0edf:bdd7]:1111,myhost2:2222/db",
            "mysql", "2001:0660:7401:0200:0000:0000:0edf:bdd7", 1111, "db");
        testUrl("jdbc:mysql://myhost1,myhost2:2222/db", "mysql", "myhost1", 3306, "db");
        testUrl("jdbc:mysql://sandy:secret@[myhost1:1111,myhost2:2222]/db", "mysql", "myhost1", 1111, "db");
        testUrl("jdbc:mysql://sandy:secret@[ myhost1:1111 ,myhost2:2222]/db", "mysql", "myhost1", 1111, "db");
        testUrl("jdbc:mysql://address=(host=myhost1)(port=1111)(key1=value1),address=(host=myhost2)(port=2222)(key2=value2)/db",
            "mysql", "myhost1", 1111, "db");
        testUrl("jdbc:mysql://address=(key1=value1)(port=1111)(host=myhost1),address=(host=myhost2)(port=2222)(key2=value2)/db",
            "mysql", "myhost1", 1111, "db");
        testUrl("jdbc:mysql:// address= ( host = myhost1 )( port = 1111 )( key1 = value1 ) ,address=(host=myhost2)(port=2222)(key2=value2)/db",
            "mysql", "myhost1", 1111, "db");
        testUrl("jdbc:mysql://(host=myhost1,port=1111,key1=value1),(host=myhost2,port=2222,key2=value2)/db",
            "mysql", "myhost1", 1111, "db");
        testUrl("jdbc:mysql://( host =  myhost1 , port  = 1111  ,key1=value1),(host=myhost2,port=2222,key2=value2)/db",
            "mysql", "myhost1", 1111, "db");
        testUrl("jdbc:mysql://(host=myhost1,port=1111),(host=myhost2,port=2222)/db?key1=value1&key2=value2&key3=value3",
            "mysql", "myhost1", 1111, "db");
        testUrl("jdbc:mysql://(port=1111,key1=value1,host=myhost1),(host=myhost2,port=2222,key2=value2)/db",
            "mysql", "myhost1", 1111, "db");
        testUrl("jdbc:mysql://myhost1:1111,(host=myhost2,port=2222,key2=value2)/db", "mysql", "myhost1", 1111, "db");
        testUrl("jdbc:mysql://sandy:secret@[myhost1:1111,myhost2:2222]/db", "mysql", "myhost1", 1111, "db");
        testUrl("jdbc:mysql://sandy:secret@[address=(host=myhost1)(port=1111)(key1=value1),address=(host=myhost2)(port=2222)(key2=value2)]/db",
            "mysql", "myhost1", 1111, "db");
        testUrl("jdbc:mysql://sandy:secret@[myhost1:1111,address=(host=myhost2)(port=2222)(key2=value2)]/db", "mysql", "myhost1", 1111, "db");
        testUrl("jdbc:mysql://sandy:secret@[address=(host=myhost2)(port=2222)(key2=value2),myhost1:1111]/db", "mysql", "myhost2", 2222, "db");
        testUrl("jdbc:mysql://[address=(host=myhost2)(port=2222)(key2=value2),myhost1:1111]/db", "mysql", "myhost2", 2222, "db");
        testUrl("jdbc:mysql://[[2001:0660:7401:0200:0000:0000:0edf:bdd7]:666,myhost1:1111]/db",
            "mysql", "2001:0660:7401:0200:0000:0000:0edf:bdd7", 666, "db");
        testUrl("jdbc:mysql://[(host=myhost1,port=1111,user=sandy,password=secret),(host=myhost2,port=2222,user=finn,password=secret)]/db",
            "mysql", "myhost1", 1111, "db");
        testUrl("jdbc:mysql://address=(host=myhost1)(port=1111)(user=sandy)(password=secret),address=(host=myhost2)(port=2222)(user=finn)(password=secret)/db",
            "mysql", "myhost1", 1111, "db");

        // load balance format:
        testUrl("jdbc:mysql:loadbalance://127.0.0.1:3309,localhost:3310/test?loadBalanceConnectionGroup=first&ha.enableJMX=true",
            "mysql", "127.0.0.1", 3309, "test");

        // replication format:
        testUrl("jdbc:mysql:replication://master,slave1,slave2,slave3/test", "mysql", "master", 3306, "test");
    }

    @Test
    void testMariadb() {
        // https://mariadb.com/kb/en/about-mariadb-connector-j/#connection-strings
        // Just like MySQL, but although not documented, seems to also allow the form: jdbc:mariadb:myhost:666/database
        testUrl("jdbc:mariadb://myhost:666/database", "mariadb", "myhost", 666, "database");
        testUrl("jdbc:mariadb://myhost/database", "mariadb", "myhost", 3306, "database");
        testUrl("jdbc:mariadb://myhost:666/", "mariadb", "myhost", 666, null);
        testUrl("jdbc:mariadb://myhost/", "mariadb", "myhost", 3306, null);
        testUrl("jdbc:mariadb://127.0.0.1/", "mariadb", "127.0.0.1", 3306, null);
        testUrl("jdbc:mariadb://::1/", "mariadb", "::1", 3306, null);
        testUrl("jdbc:mariadb://[::1]/", "mariadb", "::1", 3306, null);
        testUrl("jdbc:mariadb://[::1]:666/", "mariadb", "::1", 666, null);

        testUrl("jdbc:mariadb://myhost", "mariadb", "myhost", 3306, null);
        testUrl("jdbc:mariadb://myhost:666/database?prop1=val1&prop2=val2", "mariadb", "myhost", 666, "database");
        testUrl("jdbc:mariadb://myhost:666?prop1=val1&prop2=val2", "mariadb", "myhost", 666, null);

        testUrl("jdbc:mariadb:myhost:666/database", "mariadb", "myhost", 666, "database");
        testUrl("jdbc:mariadb:myhost:666/database?prop1=val1&prop2=val2", "mariadb", "myhost", 666, "database");
        testUrl("jdbc:mariadb:myhost/database", "mariadb", "myhost", 3306, "database");
        testUrl("jdbc:mariadb:myhost/database?prop1=val1&prop2=val2", "mariadb", "myhost", 3306, "database");
        testUrl("jdbc:mariadb:myhost:666", "mariadb", "myhost", 666, null);
        testUrl("jdbc:mariadb:myhost:666?prop1=val1&prop2=val2", "mariadb", "myhost", 666, null);
        testUrl("jdbc:mariadb:myhost", "mariadb", "myhost", 3306, null);
        testUrl("jdbc:mariadb:myhost?prop1=val1&prop2=val2", "mariadb", "myhost", 3306, null);

        // multiple hosts:
        testUrl("jdbc:mariadb://myhost1:1111,myhost2:2222/db", "mariadb", "myhost1", 1111, "db");
        testUrl("jdbc:mariadb://myhost1:1111,myhost2:2222/db?prop=val", "mariadb", "myhost1", 1111, "db");
        testUrl("jdbc:mariadb://myhost1:1111,myhost2:2222?prop=val", "mariadb", "myhost1", 1111, null);
        testUrl("jdbc:mariadb://[2001:0660:7401:0200:0000:0000:0edf:bdd7]:1111,myhost2:2222/db",
            "mariadb", "2001:0660:7401:0200:0000:0000:0edf:bdd7", 1111, "db");
        testUrl("jdbc:mariadb://myhost1,myhost2:2222/db", "mariadb", "myhost1", 3306, "db");
        testUrl("jdbc:mariadb://sandy:secret@[myhost1:1111,myhost2:2222]/db", "mariadb", "myhost1", 1111, "db");
        testUrl("jdbc:mariadb://sandy:secret@[ myhost1:1111 ,myhost2:2222]/db", "mariadb", "myhost1", 1111, "db");
        testUrl("jdbc:mariadb://address=(host=myhost1)(port=1111)(key1=value1),address=(host=myhost2)(port=2222)(key2=value2)/db",
            "mariadb", "myhost1", 1111, "db");
        testUrl("jdbc:mariadb://address=(key1=value1)(port=1111)(host=myhost1),address=(host=myhost2)(port=2222)(key2=value2)/db",
            "mariadb", "myhost1", 1111, "db");
        testUrl("jdbc:mariadb:// address= ( host = myhost1 )( port = 1111 )( key1 = value1 ) ,address=(host=myhost2)(port=2222)(key2=value2)/db",
            "mariadb", "myhost1", 1111, "db");
        testUrl("jdbc:mariadb://(host=myhost1,port=1111,key1=value1),(host=myhost2,port=2222,key2=value2)/db",
            "mariadb", "myhost1", 1111, "db");
        testUrl("jdbc:mariadb://( host =  myhost1 , port  = 1111  ,key1=value1),(host=myhost2,port=2222,key2=value2)/db",
            "mariadb", "myhost1", 1111, "db");
        testUrl("jdbc:mariadb://(host=myhost1,port=1111),(host=myhost2,port=2222)/db?key1=value1&key2=value2&key3=value3",
            "mariadb", "myhost1", 1111, "db");
        testUrl("jdbc:mariadb://(port=1111,key1=value1,host=myhost1),(host=myhost2,port=2222,key2=value2)/db",
            "mariadb", "myhost1", 1111, "db");
        testUrl("jdbc:mariadb://myhost1:1111,(host=myhost2,port=2222,key2=value2)/db", "mariadb", "myhost1", 1111, "db");
        testUrl("jdbc:mariadb://sandy:secret@[myhost1:1111,myhost2:2222]/db", "mariadb", "myhost1", 1111, "db");
        testUrl("jdbc:mariadb://sandy:secret@[address=(host=myhost1)(port=1111)(key1=value1),address=(host=myhost2)(port=2222)(key2=value2)]/db",
            "mariadb", "myhost1", 1111, "db");
        testUrl("jdbc:mariadb://sandy:secret@[myhost1:1111,address=(host=myhost2)(port=2222)(key2=value2)]/db", "mariadb", "myhost1", 1111, "db");
        testUrl("jdbc:mariadb://sandy:secret@[address=(host=myhost2)(port=2222)(key2=value2),myhost1:1111]/db", "mariadb", "myhost2", 2222, "db");
        testUrl("jdbc:mariadb://[address=(host=myhost2)(port=2222)(key2=value2),myhost1:1111]/db", "mariadb", "myhost2", 2222, "db");
        testUrl("jdbc:mariadb://[[2001:0660:7401:0200:0000:0000:0edf:bdd7]:666,myhost1:1111]/db",
            "mariadb", "2001:0660:7401:0200:0000:0000:0edf:bdd7", 666, "db");
        testUrl("jdbc:mariadb://[(host=myhost1,port=1111,user=sandy,password=secret),(host=myhost2,port=2222,user=finn,password=secret)]/db",
            "mariadb", "myhost1", 1111, "db");
        testUrl("jdbc:mariadb://address=(host=myhost1)(port=1111)(user=sandy)(password=secret),address=(host=myhost2)(port=2222)(user=finn)(password=secret)/db",
            "mariadb", "myhost1", 1111, "db");

        // Specialized formats: https://mariadb.com/kb/en/about-mariadb-connector-j/#failover-and-load-balancing-modes
        // load balance format:
        testUrl("jdbc:mariadb:loadbalance://127.0.0.1:3309,localhost:3310/test?loadBalanceConnectionGroup=first&ha.enableJMX=true",
            "mariadb", "127.0.0.1", 3309, "test");
        testUrl("jdbc:mariadb:loadbalance:myhost1:3309,localhost:3310/test?loadBalanceConnectionGroup=first&ha.enableJMX=true",
            "mariadb", "myhost1", 3309, "test");

        // replication format:
        testUrl("jdbc:mariadb:replication://master,slave1,slave2,slave3/test", "mariadb", "master", 3306, "test");
        testUrl("jdbc:mariadb:replication:master,slave1,slave2,slave3/test", "mariadb", "master", 3306, "test");
    }

    @Test
    void testSqlserver() {
        // https://docs.microsoft.com/en-us/sql/connect/jdbc/building-the-connection-url?view=sql-server-ver15
        testUrl("jdbc:sqlserver://myhost\\instance:666", "mssql", "myhost", 666, "instance");
        testUrl("jdbc:sqlserver://myhost\\instance:666;prop1=val1;prop2=val2", "mssql", "myhost", 666, "instance");
        testUrl("jdbc:sqlserver://myhost:666", "mssql", "myhost", 666, null);
        testUrl("jdbc:sqlserver://myhost:666;prop1=val1;prop2=val2", "mssql", "myhost", 666, null);
        testUrl("jdbc:sqlserver://myhost\\instance", "mssql", "myhost", 1433, "instance");
        testUrl("jdbc:sqlserver://myhost\\instance;prop1=val1;prop2=val2", "mssql", "myhost", 1433, "instance");
        testUrl("jdbc:sqlserver://myhost", "mssql", "myhost", 1433, null);
        testUrl("jdbc:sqlserver://myhost;prop1=val1;prop2=val2", "mssql", "myhost", 1433, null);
        testUrl("jdbc:sqlserver://", "mssql", "localhost", 1433, null);
        testUrl("jdbc:sqlserver://;prop1=val1;prop2=val2", "mssql", "localhost", 1433, null);
        testUrl("jdbc:sqlserver://;", "mssql", "localhost", 1433, null);
        testUrl("jdbc:sqlserver://;serverName=myhost", "mssql", "myhost", 1433, null);
        testUrl("jdbc:sqlserver://;prop1=val1;serverName=myhost", "mssql", "myhost", 1433, null);
        testUrl("jdbc:sqlserver://;serverName=myhost;prop1=val1", "mssql", "myhost", 1433, null);
        testUrl("jdbc:sqlserver://;serverName=myhost\\instance;prop1=val1", "mssql", "myhost", 1433, "instance");
        testUrl("jdbc:sqlserver://;serverName=3ffe:8311:eeee:f70f:0:5eae:10.203.31.9\\instance;prop1=val1",
            "mssql", "3ffe:8311:eeee:f70f:0:5eae:10.203.31.9", 1433, "instance");
    }

    @Test
    void testDb2() {
        // https://www.ibm.com/support/knowledgecenter/SSEPGG_11.5.0/com.ibm.db2.luw.apdv.java.doc/src/tpc/imjcc_tjvjcccn.html
        testUrl("jdbc:db2://myhost:666/mydb:user=dbadm;password=dbadm;", "db2", "myhost", 666, "mydb");
        testUrl("jdbc:db2://[::1]:666/mydb:user=dbadm;password=dbadm;", "db2", "::1", 666, "mydb");
        testUrl("jdbc:db2://127.0.0.1:666/mydb:user=dbadm;password=dbadm;", "db2", "127.0.0.1", 666, "mydb");
        testUrl("jdbc:db2://myhost/mydb:user=dbadm;password=dbadm;", "db2", "myhost", 50000, "mydb");
        testUrl("jdbc:db2://myhost;", "db2", "myhost", 50000, null);
        testUrl("jdbc:db2://my.host;", "db2", "my.host", 50000, null);
        testUrl("jdbc:db2://myhost", "db2", "myhost", 50000, null);
    }

    @Test
    void testH2() {
        // http://www.h2database.com/html/features.html#database_url
        testUrl("jdbc:h2:file:/data/sample", "h2", "localhost", -1, "/data/sample");
        testUrl("jdbc:h2:mem:", "h2", "localhost", -1, null);
        testUrl("jdbc:h2:mem:test_mem", "h2", "localhost", -1, "test_mem");
        testUrl("jdbc:h2:tcp://server/mem:test", "h2", "server", -1, "test");
        testUrl("jdbc:h2:tcp://dbserv:8084/~/sample", "h2", "dbserv", 8084, "sample");
        testUrl("jdbc:h2:ssl://dbserv:8085/~/sample;", "h2", "dbserv", 8085, "sample");
        testUrl("jdbc:h2:ssl://dbserv:8085/~/sample;prop1=val1;prop2=val2", "h2", "dbserv", 8085, "sample");
    }

    @Test
    void testGeneric() {
        testUrl("jdbc:arbitrary://MYHOST.db:666/mydb;user=dbadm;password=dbadm;", "arbitrary", "myhost.db", 666, "mydb");
        testUrl("jdbc:arbitrary://[::1]:666/mydb?user=dbadm&password=dbadm;", "arbitrary", "::1", 666, "mydb");
        testUrl("jdbc:arbitrary://127.0.0.1:666/mydb;user=dbadm;password=dbadm;", "arbitrary", "127.0.0.1", 666, "mydb");
        testUrl("jdbc:arbitrary://myhost/mydb;user=dbadm;password=dbadm;", "arbitrary", "myhost", -1, "mydb");
        testUrl("jdbc:arbitrary://myhost;", "arbitrary", "myhost", -1, null);
        testUrl("jdbc:arbitrary://my.host;", "arbitrary", "my.host", -1, null);
        testUrl("jdbc:arbitrary://myhost", "arbitrary", "myhost", -1, null);
    }

    @Test
    void testDerby() {
        // https://db.apache.org/derby/docs/10.5/devguide/rdevdvlp22102.html
        testUrl("jdbc:derby://my.host/memory:mydb;prop1=val1;prop2=val2", "derby", "my.host", 1527, "mydb");
        testUrl("jdbc:derby://my.host/memory:mydb;prop1=val1;prop2=val2", "derby", "my.host", 1527, "mydb");
        testUrl("jdbc:derby://my.host:666/memory:mydb;prop1=val1;prop2=val2", "derby", "my.host", 666, "mydb");
        testUrl("jdbc:derby:jar:/mydb;prop1=val1;prop2=val2", "derby", "localhost", -1, "/mydb");
        testUrl("jdbc:derby:memory:mydb", "derby", "localhost", -1, "mydb");
        testUrl("jdbc:derby:mydb", "derby", "localhost", -1, "mydb");
        testUrl("jdbc:derby:classpath:/myDB", "derby", "localhost", -1, "/myDB");
        testUrl("jdbc:derby:jar:(C:/dbs.jar)products/boiledfood", "derby", "localhost", -1, "(C:/dbs.jar)products/boiledfood");
        testUrl("jdbc:derby:directory:myDB", "derby", "localhost", -1, "myDB");
    }

    @Test
    void testHsqldb() {
        // http://hsqldb.org/doc/2.0/guide/dbproperties-chapt.html
        testUrl("jdbc:hsqldb:file:~/mydb", "hsqldb", "localhost", -1, "~/mydb");
        testUrl("jdbc:hsqldb:file:enrolments;user=aUserName;ifexists=true", "hsqldb", "localhost", -1, "enrolments");
        testUrl("jdbc:hsqldb:hsql://localhost/enrolments;close_result=true", "hsqldb", "localhost", 9001, "enrolments");
        testUrl("jdbc:hsqldb:hsql://my.host/enrolments;close_result=true", "hsqldb", "my.host", 9001, "enrolments");
        testUrl("jdbc:hsqldb:http://192.0.0.10:9500", "hsqldb", "192.0.0.10", 9500, null);
        testUrl("jdbc:hsqldb:http://dbserver.somedomain.com", "hsqldb", "dbserver.somedomain.com", 9001, null);
        testUrl("jdbc:hsqldb:mem:", "hsqldb", "localhost", -1, null);
        testUrl("jdbc:hsqldb:res:/adirectory/dbname", "hsqldb", "localhost", -1, "/adirectory/dbname");
    }

    @Test
    void testInvalid() {
        // invalid URL, just checking the defaults, assuming "localhost" and no port as if it would be an in-memory database
        testUrl("", "unknown", "localhost", -1, null);
    }

    private static final MockTracer.MockInstrumentationSetup mockSetup = MockTracer.createMockInstrumentationSetup();
    private static final MockReporter reporter = mockSetup.getReporter();
    private static final ElasticApmTracer tracer = mockSetup.getTracer();

    private void testUrl(String url, String expectedVendor, @Nullable String expectedHost, int expectedPort, @Nullable String expectedDatabase) {
        ConnectionMetaData metadata = ConnectionMetaData.parse(url).build();

        assertThat(metadata.getDbVendor())
            .describedAs("unexpected vendor for URL: %s")
            .isEqualTo(expectedVendor);
        assertThat(metadata.getHost())
            .describedAs("unexpected host for URL: %s", url)
            .isEqualTo(expectedHost);
        assertThat(metadata.getPort())
            .describedAs("unexpected port for URL: %s", url)
            .isEqualTo(expectedPort);
        assertThat(metadata.getInstance())
            .describedAs("unexpected database name for URL: %s", url)
            .isEqualTo(expectedDatabase);

        if ("arbitrary".equals(expectedVendor)) {
            // known case where the jdbc url is valid, but has an unknwon value in shared spec
            return;
        }

        // try to create a DB span in order to trigger shared span type validation logic
        // given not all JDBC urls listed here will be explicitly tested with a real database, that allows
        // to make sure that at least the known ones always fit
        try {

            TransactionImpl transaction = tracer.startRootTransaction(this.getClass().getClassLoader());
            assertThat(transaction).isNotNull();
            transaction.createSpan()
                .withType("db")
                .withSubtype(metadata.getDbVendor()).end();
            transaction.end();

            assertThat(reporter.getNumReportedSpans()).isEqualTo(1);
            assertThat(reporter.getNumReportedTransactions()).isEqualTo(1);
        } finally {
            reporter.reset();
        }
    }

    @Test
    void builderDefauts() {
        ConnectionMetaData metaData = ConnectionMetaData.parse("").build();
        assertThat(metaData.getDbVendor()).isEqualTo("unknown");
        assertThat(metaData.getHost()).isEqualTo("localhost");
        assertThat(metaData.getPort()).isLessThan(0);
        assertThat(metaData.getUser()).isNull();
    }

    @Test
    void builderLocalAccess() {
        ConnectionMetaData metaData = ConnectionMetaData.parse("")
            .withLocalAccess()
            .build();

        assertThat(metaData.getHost()).isEqualTo("localhost");
        assertThat(metaData.getPort()).isLessThan(0);
    }

    @Test
    void builderBaseUsage() {
        ConnectionMetaData metaData = ConnectionMetaData.parse("")
            .withVendor("vendor")
            .withUser("user")
            .withConnectionUser("connection-user") // ignored as already set
            .withHost("[::1]")
            .withInstance("instance")
            .withConnectionInstance("connection-instance") // ignored as already set
            .withPort(42)
            .build();

        assertThat(metaData.getDbVendor()).isEqualTo("vendor");
        assertThat(metaData.getUser()).isEqualTo("user");
        assertThat(metaData.getHost())
            .describedAs("host name IPv6 is normalized")
            .isEqualTo("::1");
        assertThat(metaData.getInstance()).isEqualTo("instance");
        assertThat(metaData.getPort()).isEqualTo(42);
    }

    @Test
    void builderConnectionFallback() {
        ConnectionMetaData metaData = ConnectionMetaData.parse("")
            .withVendor("vendor")
            .withConnectionUser("connection-user") // ignored as already set
            .withHost("HOST.db")
            .withConnectionInstance("connection-instance") // ignored as already set
            .withPort(42)
            .build();

        assertThat(metaData.getDbVendor()).isEqualTo("vendor");
        assertThat(metaData.getUser()).isEqualTo("connection-user");
        assertThat(metaData.getHost())
            .describedAs("host name should be normalized to lower-case")
            .isEqualTo("host.db");
        assertThat(metaData.getInstance()).isEqualTo("connection-instance");
        assertThat(metaData.getPort()).isEqualTo(42);
    }

    @ParameterizedTest
    @CsvSource(nullValues = {"null"}, delimiter = '|', value = {
        "database|localhost|-1|database",
        "//|localhost|-1|null",
        "///|localhost|-1|null",
        "//host/|host|-1|null",
        "//host/database|host|-1|database",
        "//host:666/database|host|666|database",
        "//host:666/database|host|666|database",
        "//host:666/database?prop1=val1&prop2=val2=val|host|666|database",
        "//host:666/database;prop1=val1;prop2=val2|host|666|database"
    })
    void defaultParse(String vendorUrl, String expectedHost, int expectedPort, String expectedDatabase) {
        ConnectionMetaData metaData = ConnectionMetaData.parse("jdbc:unsupported:" + vendorUrl).build();
        assertThat(metaData.getHost()).isEqualTo(expectedHost);
        assertThat(metaData.getPort()).isEqualTo(expectedPort);
        assertThat(metaData.getInstance()).isEqualTo(expectedDatabase);
    }

}
