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

    public JdbcDbIT(String url, String expectedDbVendor) throws Exception {
        super(DriverManager.getConnection(url), expectedDbVendor);
    }

    @Parameterized.Parameters(name = "{0} {1}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"jdbc:tc:mysql:5://hostname/databasename", "mysql"},
            {"jdbc:tc:postgresql:9://hostname/databasename", "postgresql"},
            {"jdbc:tc:postgresql:10://hostname/databasename", "postgresql"},
            {"jdbc:tc:mariadb:10://hostname/databasename", "mariadb"},
            {"jdbc:tc:sqlserver:2017-CU12://hostname/databasename", "sqlserver"},
        });
    }

}
