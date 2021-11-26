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
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.jdbc.JDBCConfiguration;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

public class JdbcHelperTest extends AbstractInstrumentationTest {

    @BeforeAll
    static void setUp() {
        when(config.getConfig(JDBCConfiguration.class).getSQLsExcludedFromInstrumentation())
            .thenReturn(Collections.singletonList(WildcardMatcher.valueOf("SELECT 1")));
    }

    @Test
    void testExcludedSqls() throws SQLException {
        Transaction transaction = startTestRootTransaction();
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:test");
             Statement s = c.createStatement()) {
            s.execute("SELECT 1");
        } finally {
            transaction.deactivate().end();
        }

        assertThat(reporter.getNumReportedSpans()).isZero();
    }
}
