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

package co.elastic.apm.impl.transaction;

import co.elastic.apm.objectpool.Allocator;
import co.elastic.apm.objectpool.ObjectPool;
import co.elastic.apm.objectpool.Recyclable;
import co.elastic.apm.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.objectpool.impl.Resetter;
import co.elastic.apm.report.serialize.DslJsonSerializer;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;

import javax.annotation.Nullable;
import java.nio.CharBuffer;


/**
 * An object containing contextual data for database spans
 */
public class Db implements Recyclable {

    private final ObjectPool<CharBuffer> charBufferPool = QueueBasedObjectPool.of(new MpmcAtomicArrayQueue<CharBuffer>(128), false,
        new Allocator<CharBuffer>() {
            @Override
            public CharBuffer createInstance() {
                return CharBuffer.allocate(DslJsonSerializer.MAX_LONG_STRING_VALUE_LENGTH);
            }
        },
        new Resetter<CharBuffer>() {
            @Override
            public void recycle(CharBuffer object) {
                object.clear();
            }
        });

    /**
     * Database instance name
     */
    @Nullable
    private String instance;
    /**
     * A database statement (e.g. query) for the given database type
     */
    @Nullable
    private String statement;

    @Nullable
    private CharBuffer statementBuffer;

    /**
     * Database type. For any SQL database, "sql". For others, the lower-case database category, e.g. "cassandra", "hbase", or "redis"
     */
    @Nullable
    private String type;
    /**
     * Username for accessing database
     */
    @Nullable
    private String user;

    /**
     * Database instance name
     */
    @Nullable
    public String getInstance() {
        return instance;
    }

    /**
     * Database instance name
     */
    public Db withInstance(@Nullable String instance) {
        this.instance = instance;
        return this;
    }

    /**
     * A database statement (e.g. query) for the given database type
     */
    @Nullable
    public String getStatement() {
        return statement;
    }

    /**
     * A database statement (e.g. query) for the given database type
     */
    public Db withStatement(@Nullable String statement) {
        this.statement = statement;
        return this;
    }

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
    public CharBuffer withStatementBuffer() {
        if (this.statementBuffer == null) {
            this.statementBuffer = charBufferPool.createInstance();
        }
        return this.statementBuffer;
    }

    /**
     * Returns the associated pooled {@link CharBuffer} to record the DB statement.
     * <p>
     * Note: returns {@code null} unless {@link #withStatementBuffer()} has previously been called
     * </p>
     *
     * @return a {@link CharBuffer} to record the DB statement, or {@code null}
     */
    @Nullable
    public CharBuffer getStatementBuffer() {
        return statementBuffer;
    }

    /**
     * Database type. For any SQL database, "sql". For others, the lower-case database category, e.g. "cassandra", "hbase", or "redis"
     */
    @Nullable
    public String getType() {
        return type;
    }

    /**
     * Database type. For any SQL database, "sql". For others, the lower-case database category, e.g. "cassandra", "hbase", or "redis"
     */
    public Db withType(@Nullable String type) {
        this.type = type;
        return this;
    }

    /**
     * Username for accessing database
     */
    @Nullable
    public String getUser() {
        return user;
    }

    /**
     * Username for accessing database
     */
    public Db withUser(@Nullable String user) {
        this.user = user;
        return this;
    }

    @Override
    public void resetState() {
        instance = null;
        statement = null;
        type = null;
        user = null;
        if (statementBuffer != null) {
            charBufferPool.recycle(statementBuffer);
        }
        statementBuffer = null;
    }

    public boolean hasContent() {
        return instance != null ||
            statement != null ||
            type != null ||
            user != null ||
            statementBuffer != null;
    }

    public void copyFrom(Db other) {
        instance = other.instance;
        statement = other.statement;
        type = other.type;
        user = other.user;
    }
}
