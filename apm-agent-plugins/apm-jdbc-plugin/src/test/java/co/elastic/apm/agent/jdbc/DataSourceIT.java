/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.jdbc;

import com.alibaba.druid.pool.DruidDataSource;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.vibur.dbcp.ViburDBCPDataSource;

import javax.sql.DataSource;
import java.util.function.Supplier;

import static java.util.Arrays.asList;

// inspired by https://github.com/testcontainers/testcontainers-java/blob/master/modules/jdbc-test/src/test/java/org/testcontainers/jdbc/JDBCDriverWithPoolTest.java
@RunWith(Parameterized.class)
public class DataSourceIT extends AbstractJdbcInstrumentationTest {

    private static final String URL = "jdbc:h2:mem:test";

    public DataSourceIT(Supplier<DataSource> dataSourceSupplier) throws Exception {
        super(dataSourceSupplier.get().getConnection(), "h2");
    }

    @Parameterized.Parameters
    public static Iterable<Supplier<DataSource>> dataSourceSuppliers() {
        return asList(
            DataSourceIT::getTomcatDataSource,
            DataSourceIT::getHikariDataSource,
            DataSourceIT::getViburDataSource,
            DataSourceIT::getDruidDataSource,
            DataSourceIT::getDbcpDataSource,
            DataSourceIT::getDbcp2DataSource,
            DataSourceIT::getC3p0DataSource
        );
    }

    private static DataSource getTomcatDataSource() {
        PoolProperties poolProperties = new PoolProperties();
        poolProperties.setUrl(URL);
        return new org.apache.tomcat.jdbc.pool.DataSource(poolProperties);
    }

    private static HikariDataSource getHikariDataSource() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(URL);
        return new HikariDataSource(hikariConfig);
    }

    private static DataSource getViburDataSource() {
        ViburDBCPDataSource ds = new ViburDBCPDataSource();
        ds.setJdbcUrl(URL);
        ds.setUsername("");
        ds.setPassword("");
        ds.start();
        return ds;
    }

    private static DataSource getDruidDataSource() {
        DruidDataSource druidDataSource = new DruidDataSource();
        druidDataSource.setUrl(URL);
        druidDataSource.setTestWhileIdle(false);
        return druidDataSource;
    }

    private static DataSource getDbcpDataSource() {
        org.apache.commons.dbcp.BasicDataSource dbcp = new org.apache.commons.dbcp.BasicDataSource();
        dbcp.setUrl(URL);
        return dbcp;
    }

    private static DataSource getDbcp2DataSource() {
        org.apache.commons.dbcp2.BasicDataSource dbcp2 = new org.apache.commons.dbcp2.BasicDataSource();
        dbcp2.setUrl(URL);
        return dbcp2;
    }

    private static DataSource getC3p0DataSource() {
        ComboPooledDataSource comboPooledDataSource = new ComboPooledDataSource();
        comboPooledDataSource.setJdbcUrl(URL);
        return comboPooledDataSource;
    }

}
