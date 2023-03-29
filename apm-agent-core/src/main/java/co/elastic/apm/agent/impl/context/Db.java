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
package co.elastic.apm.agent.impl.context;

import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.objectpool.Resetter;
import co.elastic.apm.agent.report.serialize.SerializationConstants;
import co.elastic.apm.agent.tracer.pooling.Allocator;
import co.elastic.apm.agent.tracer.pooling.Recyclable;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;

import javax.annotation.Nullable;
import java.nio.Buffer;
import java.nio.CharBuffer;

public class Db implements Recyclable, co.elastic.apm.agent.tracer.metadata.Db {

    private static final ObjectPool<CharBuffer> charBufferPool = QueueBasedObjectPool.of(new MpmcAtomicArrayQueue<CharBuffer>(128), false,
        new Allocator<CharBuffer>() {
            @Override
            public CharBuffer createInstance() {
                return CharBuffer.allocate(SerializationConstants.getMaxLongStringValueLength());
            }
        },
        new Resetter<CharBuffer>() {
            @Override
            public void recycle(CharBuffer object) {
                ((Buffer) object).clear();
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
     * DB Link for connections between 2 databases
     */
    @Nullable
    private String dbLink;

    /**
     * Number of affected rows by statement
     */
    private long affectedRowsCount = -1;

    /**
     * Database instance name
     */
    @Nullable
    public String getInstance() {
        return instance;
    }

    @Override
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

    @Override
    public Db withStatement(@Nullable String statement) {
        this.statement = statement;
        return this;
    }

    @Override
    public CharBuffer withStatementBuffer() {
        if (this.statementBuffer == null) {
            this.statementBuffer = charBufferPool.createInstance();
        }
        return this.statementBuffer;
    }

    @Override
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

    @Override
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

    @Override
    public Db withUser(@Nullable String user) {
        this.user = user;
        return this;
    }

    /**
     * DB Link for connections between 2 databases
     */
    @Nullable
    public String getDbLink() {
        return dbLink;
    }

    /**
     * DB Link for connections between 2 databases
     */
    public Db withDbLink(@Nullable String dbLink) {
        this.dbLink = dbLink;
        return this;
    }

    /**
     * @return number of affected rows by statement execution. A negative value might indicate feature is not supported by db/driver.
     */
    public long getAffectedRowsCount(){
        return affectedRowsCount;
    }

    @Override
    public Db withAffectedRowsCount(long count){
        this.affectedRowsCount = count;
        return this;
    }

    @Override
    public void resetState() {
        instance = null;
        statement = null;
        type = null;
        user = null;
        dbLink = null;
        if (statementBuffer != null) {
            charBufferPool.recycle(statementBuffer);
        }
        statementBuffer = null;
        affectedRowsCount = -1;
    }

    public boolean hasContent() {
        return instance != null ||
            statement != null ||
            type != null ||
            user != null ||
            dbLink != null ||
            statementBuffer != null;
    }

    public void copyFrom(Db other) {
        instance = other.instance;
        statement = other.statement;
        type = other.type;
        user = other.user;
        dbLink = other.dbLink;
    }
}
