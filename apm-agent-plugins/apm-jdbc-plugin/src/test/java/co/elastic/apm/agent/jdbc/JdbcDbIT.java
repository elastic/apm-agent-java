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
package co.elastic.apm.agent.jdbc;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.sql.DriverManager;
import java.util.Arrays;

@RunWith(Parameterized.class)
public class JdbcDbIT extends AbstractJdbcInstrumentationTest {

    static {
        System.setProperty("oracle.jdbc.timezoneAsRegion", "false");
    }

    public JdbcDbIT(String url, String expectedDbVendor, String expectedDbName, boolean dbNameFromUrl) throws Exception {
        super(DriverManager.getConnection(url), expectedDbVendor, expectedDbName, dbNameFromUrl);
    }

    @Parameterized.Parameters(name = "{1} {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            // we are using testcontainers JDBC url format, the actual driver URL is not the same
            {"jdbc:tc:mysql:5://hostname/databasename", "mysql", "databasename", true},
            {"jdbc:tc:postgresql:9://hostname/databasename", "postgresql", "databasename", true},
            {"jdbc:tc:postgresql:10://hostname/databasename", "postgresql", "databasename", true},
            {"jdbc:tc:mariadb:10://hostname/databasename", "mariadb", "databasename", true},
            // TODO: SQL Server image seems to be broken with recent kernel versions: https://github.com/microsoft/mssql-docker/issues/868
            //{"jdbc:tc:sqlserver:2017-CU12://hostname/databasename", "mssql", "master", false}, // for mssql the 'master' name comes from the runtime catalog fallback
            {"jdbc:tc:db2:11.5.0.0a://hostname/databasename", "db2", "test", true},
            {"jdbc:tc:oracle://hostname/databasename", "oracle", "xepdb1", true},
        });
    }

}
