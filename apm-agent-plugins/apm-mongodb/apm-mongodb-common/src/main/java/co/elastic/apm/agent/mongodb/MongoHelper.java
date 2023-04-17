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
package co.elastic.apm.agent.mongodb;

import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.Tracer;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import javax.annotation.Nullable;

public class MongoHelper {

    private static final Logger logger = LoggerFactory.getLogger(MongoHelper.class);

    private final Tracer tracer;
    private final MongoConfiguration config;

    public MongoHelper() {
        this.tracer = GlobalTracer.get();
        this.config = tracer.getConfig(MongoConfiguration.class);
    }

    public Span<?> startSpan(@Nullable String database, @Nullable String collection, @Nullable String command, String host, int port, @Nullable BsonDocument commandDocument) {
        Span<?> span = null;
        final AbstractSpan<?> activeSpan = tracer.getActive();
        if (activeSpan != null) {
            span = activeSpan.createExitSpan();
        }

        if (span == null) {
            return null;
        }

        span.withType("db")
            .withSubtype("mongodb")
            .withAction(command)
            .getContext().getDb().withType("mongodb");

        span.getContext().getServiceTarget()
            .withType("mongodb")
            .withName(database);

        String statement = null;
        if (command != null && commandDocument != null && WildcardMatcher.anyMatch(config.getCaptureStatementCommands(), command) != null) {
            statement = commandDocument.toJson();
        }

        span.getContext().getDb()
            .withInstance(database)
            .withStatement(statement);

        StringBuilder name = span.getAndOverrideName(AbstractSpan.PRIORITY_DEFAULT);
        if (name != null) {
            appendToName(name, database);
            appendToName(name, collection);
            appendToName(name, command);
        }

        span.getContext().getDestination()
            .withAddress(host)
            .withPort(port);

        return span.activate();
    }

    @Nullable
    public String getCommandFromBson(BsonDocument document) {
        String cmd = null;
        try {
            // try to determine main commands in a garbage free way
            cmd =
                document.containsKey("find") ? "find" :
                    document.containsKey("insert") ? "insert" :
                        document.containsKey("count") ? "count" :
                            document.containsKey("drop") ? "drop" :
                                document.containsKey("update") ? "update" :
                                    document.containsKey("delete") ? "delete" :
                                        document.containsKey("create") ? "create" :
                                            document.containsKey("getMore") ? "getMore" :
                                                // fall back to getting the first key which is the command name
                                                // by allocating a key set and an iterator. Identical to getFirstKey in bson 3.6+
                                                document.keySet().iterator().next();

        } catch (RuntimeException e) {
            logger.error("Exception while determining MongoDB command and collection", e);
        }
        return cmd;
    }

    @Nullable
    public String getCollectionFromBson(String name, BsonDocument document) {

        String collection = null;
        BsonValue bsonValue = document.get(name);
        if (bsonValue != null && bsonValue.isString()) {
            collection = bsonValue.asString().getValue();
        }

        if (collection == null) {
            bsonValue = document.get("collection");
            if (bsonValue != null && bsonValue.isString()) {
                collection = bsonValue.asString().getValue();
            }
        }

        return collection;
    }

    private static void appendToName(StringBuilder name, @Nullable String value) {
        if (value == null) {
            return;
        }
        if (name.length() > 0) {
            name.append('.');
        }
        name.append(value);
    }
}
