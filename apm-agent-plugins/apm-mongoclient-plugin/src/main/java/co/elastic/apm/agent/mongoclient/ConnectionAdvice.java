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
package co.elastic.apm.agent.mongoclient;

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import com.mongodb.MongoNamespace;
import com.mongodb.ServerAddress;
import com.mongodb.connection.Connection;
import net.bytebuddy.asm.Advice;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;

public class ConnectionAdvice {

    public static final Logger logger = LoggerFactory.getLogger(ConnectionAdvice.class);

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnter(@Advice.This Connection thiz,
                               @Advice.Argument(0) Object databaseOrMongoNamespace,
                               @Advice.Argument(1) BsonDocument command) {
        Span span = null;
        final AbstractSpan<?> activeSpan = GlobalTracer.get().getActive();
        if (activeSpan != null && !activeSpan.isExit()) {
            span = activeSpan.createExitSpan();
        }

        if (span == null) {
            return null;
        }

        String database = "";
        String collection = null;
        if (databaseOrMongoNamespace instanceof String) {
            database = (String) databaseOrMongoNamespace;
        } else if (databaseOrMongoNamespace instanceof MongoNamespace) {
            MongoNamespace namespace = (MongoNamespace) databaseOrMongoNamespace;
            database = namespace.getDatabaseName();
            collection = namespace.getCollectionName();
        }

        span.withType("db").withSubtype("mongodb")
            .appendToName(database).getContext().getDb().withType("mongodb");
        span.getContext().getDestination().getService()
            .withName("mongodb").withResource("mongodb").withType("db");
        ServerAddress serverAddress = thiz.getDescription().getServerAddress();
        span.getContext().getDestination()
            .withAddress(serverAddress.getHost())
            .withPort(serverAddress.getPort());
        try {
            String cmd =
                // try to determine main commands in a garbage free way
                command.containsKey("find") ? "find" :
                command.containsKey("insert") ? "insert" :
                command.containsKey("count") ? "count" :
                command.containsKey("drop") ? "drop" :
                command.containsKey("update") ? "update" :
                command.containsKey("delete") ? "delete" :
                command.containsKey("create") ? "create" :
                command.containsKey("getMore") ? "getMore" :
                // fall back to getting the first key which is the command name
                // by allocating a key set and an iterator
                command.keySet().iterator().next();
            if (collection == null) {
                BsonValue collectionName = command.get(cmd);
                if (collectionName != null && collectionName.isString()) {
                    collection = collectionName.asString().getValue();
                    span.appendToName(".").appendToName(collection);
                }
            }
            if (collection == null) {
                BsonValue collectionName = command.get("collection");
                if (collectionName != null && collectionName.isString()) {
                    collection = collectionName.asString().getValue();
                    span.appendToName(".").appendToName(collection);
                }
            }
            span.appendToName(".").appendToName(cmd).withAction(cmd);
        } catch (RuntimeException e) {
            logger.error("Exception while determining MongoDB command and collection", e);
        }
        span.activate();
        return span;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(@Nullable @Advice.Enter Object spanObj, @Advice.Thrown Throwable thrown, @Advice.Origin("#m") String methodName) {
        if (spanObj instanceof Span) {
            Span span = (Span) spanObj;
            span.deactivate().captureException(thrown);
            if (!methodName.endsWith("Async")) {
                span.end();
            }
        }
    }

}
