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
package co.elastic.apm.agent.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;

class TestStatement implements Statement {
    private final Statement delegate;

    private boolean isGetConnectionSupported;
    private int unsupportedThrownCount;

    private Connection connection;

    public TestStatement(Statement delegate) {
        this.delegate = delegate;
        this.unsupportedThrownCount = 0;
        this.isGetConnectionSupported = true;
    }

    private void unsupportedCheck(boolean isFeatureSupported) throws SQLException {
        if (!isFeatureSupported) {
            unsupportedThrownCount++;
            throw new SQLException("Not supported");
        }
    }

    public void setGetConnectionSupported(boolean supported) {
        this.isGetConnectionSupported = supported;
    }

    int getUnsupportedThrownCount(){
        return unsupportedThrownCount;
    }

    void setConnection(Connection connection) {
        this.connection = connection;
    }

    public ResultSet executeQuery(String sql) throws SQLException {
        return delegate.executeQuery(sql);
    }

    public int executeUpdate(String sql) throws SQLException {
        return delegate.executeUpdate(sql);
    }

    public void close() throws SQLException {
        delegate.close();
    }

    public int getMaxFieldSize() throws SQLException {
        return delegate.getMaxFieldSize();
    }

    public void setMaxFieldSize(int max) throws SQLException {
        delegate.setMaxFieldSize(max);
    }

    public int getMaxRows() throws SQLException {
        return delegate.getMaxRows();
    }

    public void setMaxRows(int max) throws SQLException {
        delegate.setMaxRows(max);
    }

    public void setEscapeProcessing(boolean enable) throws SQLException {
        delegate.setEscapeProcessing(enable);
    }

    public int getQueryTimeout() throws SQLException {
        return delegate.getQueryTimeout();
    }

    public void setQueryTimeout(int seconds) throws SQLException {
        delegate.setQueryTimeout(seconds);
    }

    public void cancel() throws SQLException {
        delegate.cancel();
    }

    public SQLWarning getWarnings() throws SQLException {
        return delegate.getWarnings();
    }

    public void clearWarnings() throws SQLException {
        delegate.clearWarnings();
    }

    public void setCursorName(String name) throws SQLException {
        delegate.setCursorName(name);
    }

    public boolean execute(String sql) throws SQLException {
        return delegate.execute(sql);
    }

    public ResultSet getResultSet() throws SQLException {
        return delegate.getResultSet();
    }

    public int getUpdateCount() throws SQLException {
        return delegate.getUpdateCount();
    }

    public boolean getMoreResults() throws SQLException {
        return delegate.getMoreResults();
    }

    public void setFetchDirection(int direction) throws SQLException {
        delegate.setFetchDirection(direction);
    }

    public int getFetchDirection() throws SQLException {
        return delegate.getFetchDirection();
    }

    public void setFetchSize(int rows) throws SQLException {
        delegate.setFetchSize(rows);
    }

    public int getFetchSize() throws SQLException {
        return delegate.getFetchSize();
    }

    public int getResultSetConcurrency() throws SQLException {
        return delegate.getResultSetConcurrency();
    }

    public int getResultSetType() throws SQLException {
        return delegate.getResultSetType();
    }

    public void addBatch(String sql) throws SQLException {
        delegate.addBatch(sql);
    }

    public void clearBatch() throws SQLException {
        delegate.clearBatch();
    }

    public int[] executeBatch() throws SQLException {
        return delegate.executeBatch();
    }

    public Connection getConnection() throws SQLException {
        unsupportedCheck(isGetConnectionSupported);

        if (null != connection) {
            return connection;
        }
        return delegate.getConnection();
    }

    public boolean getMoreResults(int current) throws SQLException {
        return delegate.getMoreResults(current);
    }

    public ResultSet getGeneratedKeys() throws SQLException {
        return delegate.getGeneratedKeys();
    }

    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return delegate.executeUpdate(sql, autoGeneratedKeys);
    }

    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return delegate.executeUpdate(sql, columnIndexes);
    }

    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return delegate.executeUpdate(sql, columnNames);
    }

    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return delegate.execute(sql, autoGeneratedKeys);
    }

    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return delegate.execute(sql, columnIndexes);
    }

    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return delegate.execute(sql, columnNames);
    }

    public int getResultSetHoldability() throws SQLException {
        return delegate.getResultSetHoldability();
    }

    public boolean isClosed() throws SQLException {
        return delegate.isClosed();
    }

    public void setPoolable(boolean poolable) throws SQLException {
        delegate.setPoolable(poolable);
    }

    public boolean isPoolable() throws SQLException {
        return delegate.isPoolable();
    }

    public void closeOnCompletion() throws SQLException {
        delegate.closeOnCompletion();
    }

    public boolean isCloseOnCompletion() throws SQLException {
        return delegate.isCloseOnCompletion();
    }

    public long getLargeUpdateCount() throws SQLException {
        return delegate.getLargeUpdateCount();
    }

    public void setLargeMaxRows(long max) throws SQLException {
        delegate.setLargeMaxRows(max);
    }

    public long getLargeMaxRows() throws SQLException {
        return delegate.getLargeMaxRows();
    }

    public long[] executeLargeBatch() throws SQLException {
        return delegate.executeLargeBatch();
    }

    public long executeLargeUpdate(String sql) throws SQLException {
        return delegate.executeLargeUpdate(sql);
    }

    public long executeLargeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return delegate.executeLargeUpdate(sql, autoGeneratedKeys);
    }

    public long executeLargeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return delegate.executeLargeUpdate(sql, columnIndexes);
    }

    public long executeLargeUpdate(String sql, String[] columnNames) throws SQLException {
        return delegate.executeLargeUpdate(sql, columnNames);
    }

    public String enquoteLiteral(String val) throws SQLException {
        return delegate.enquoteLiteral(val);
    }

    public String enquoteIdentifier(String identifier, boolean alwaysQuote) throws SQLException {
        return delegate.enquoteIdentifier(identifier, alwaysQuote);
    }

    public boolean isSimpleIdentifier(String identifier) throws SQLException {
        return delegate.isSimpleIdentifier(identifier);
    }

    public String enquoteNCharLiteral(String val) throws SQLException {
        return delegate.enquoteNCharLiteral(val);
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        return delegate.unwrap(iface);
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return delegate.isWrapperFor(iface);
    }
}
