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
package co.elastic.apm.agent.tracer.metadata;

import javax.annotation.Nullable;
import java.nio.CharBuffer;

/**
 * An object containing contextual data for database spans
 */
public interface Db {

    /**
     * Database instance name
     */
    Db withInstance(@Nullable String instance);

    /**
     * Database type. For any SQL database, "sql". For others, the lower-case database category, e.g. "cassandra", "hbase", or "redis"
     */
    Db withType(@Nullable String type);

    /**
     * A database statement (e.g. query) for the given database type
     */
    Db withStatement(@Nullable String statement);

    /**
     * Username for accessing database
     */
    Db withUser(@Nullable String user);

    /**
     * Sets the number of affected rows by statement execution
     *
     * @param count number of affected rows
     * @return this
     */
    Db withAffectedRowsCount(long count);

    /**
     * Returns the associated pooled {@link CharBuffer} to record the DB statement.
     * <p>
     * Note: returns {@code null} unless {@link #withStatementBuffer()} has previously been called
     * </p>
     *
     * @return a {@link CharBuffer} to record the DB statement, or {@code null}
     */
    @Nullable
    CharBuffer getStatementBuffer();

    /**
     * Gets a pooled {@link CharBuffer} to record the DB statement and associates it with this instance.
     * <p>
     * Note: you may not hold a reference to the returned {@link CharBuffer} as it will be reused.
     * </p>
     * <p>
     * Note: This method is not thread safe
     * </p>
     *
     * @return a {@link CharBuffer} to record the DB statement
     */
    CharBuffer withStatementBuffer();
}
