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
package co.elastic.apm.jdbc;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.sql.DriverManager;
import java.util.Arrays;

import static co.elastic.apm.jdbc.JdbcUtils.computeJdbcSpanTypeName;

@RunWith(Parameterized.class)
public class JdbcDbIT extends AbstractJdbcInstrumentationTest {

    static {
        System.setProperty("oracle.jdbc.timezoneAsRegion", "false");
    }

    public JdbcDbIT(String url, String expectedSpanType) throws Exception {
        super(DriverManager.getConnection(url), expectedSpanType);
    }

    @Parameterized.Parameters(name = "{index}: {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"jdbc:tc:mysql:5://hostname/databasename", computeJdbcSpanTypeName("mysql")},
            {"jdbc:tc:postgresql:9://hostname/databasename", computeJdbcSpanTypeName("postgresql")},
            {"jdbc:tc:postgresql:10://hostname/databasename", computeJdbcSpanTypeName("postgresql")},
            {"jdbc:tc:mariadb:10://hostname/databasename", computeJdbcSpanTypeName("mariadb")},
            {"jdbc:tc:mssqlserver:2017-CU9://hostname/databasename", computeJdbcSpanTypeName("sqlserver")},
            {"jdbc:tc:oracle://hostname/databasename", computeJdbcSpanTypeName("oracle")},
        });
    }

}
