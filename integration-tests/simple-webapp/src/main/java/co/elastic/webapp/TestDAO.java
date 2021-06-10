/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.webapp;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class TestDAO {

    private static final TestDAO INSTANCE = new TestDAO();

    private Connection connection;

    public static TestDAO instance() {
        return INSTANCE;
    }

    private TestDAO() {
        try {
            // registers the driver in the DriverManager
            Class.forName("org.h2.Driver");
            connection = DriverManager.getConnection("jdbc:h2:mem:test", "user", "");
            final Statement connectionStatement = connection.createStatement();
            connectionStatement.execute("CREATE TABLE ELASTIC_APM (FOO INT, BAR VARCHAR(255))");
            connection.createStatement().execute("INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (1, 'Hello World!')");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String queryDb(boolean fail) throws SQLException {
        Statement statement = connection.createStatement();
        String query = String.format("SELECT * FROM ELASTIC_APM WHERE %s=1", fail ? "NON_EXISTING_COLUMN" : "FOO");
        ResultSet resultSet = statement.executeQuery(query);
        resultSet.next();
        return resultSet.getString("bar");
    }
}
