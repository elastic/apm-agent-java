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

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class JdbcGetUserNameExclusionTest extends AbstractInstrumentationTest {

    protected static JdbcConfiguration jdbcconfig;

    @Test
    public void hasUsernameCorrectlyExcludes() throws SQLException {
        DatabaseMetaData meta = (DatabaseMetaData) Proxy.newProxyInstance(
            this.getClass().getClassLoader(),
            new Class[] { DatabaseMetaData.class },
            new MetadataInvocationHandler());

        assertThat(JdbcHelper.maybeGetUserName(meta, config.getConfig(JdbcConfiguration.class))).isEqualTo("testuser");

        String classname = meta.getClass().getName();
        String excludeName = classname.substring(classname.indexOf('$')+1);
        doReturn(List.of(excludeName))
            .when(config.getConfig(JdbcConfiguration.class))
            .getDatabaseMetaDataExclusionList();

        assertThat(JdbcHelper.maybeGetUserName(meta, config.getConfig(JdbcConfiguration.class))).isEqualTo(null);
    }

    public class MetadataInvocationHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("getUserName")) {
                return "testuser";
            }
            return null;
        }
    }

}
