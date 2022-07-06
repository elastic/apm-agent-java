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
import co.elastic.apm.agent.mongodb.MongoHelper;
import com.mongodb.MongoNamespace;
import com.mongodb.ServerAddress;
import com.mongodb.connection.Connection;
import net.bytebuddy.asm.Advice;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;

public class ConnectionAdvice { // relies on Connection class which has been removed in 4.0

    public static final Logger logger = LoggerFactory.getLogger(ConnectionAdvice.class);

    // TODO : instrument com.mongodb.internal.connection.InternalConnection.sendAndReceive seems a more portable option
    // it is available both on 3.x and 4.x drivers
    // TODO :need to check for 3.0.x drivers: this is not available on oldest 3.x drivers :-(

    private static final MongoHelper helper = new MongoHelper(GlobalTracer.get());

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnter(@Advice.This Connection thiz,
                                 @Advice.Argument(0) Object databaseOrMongoNamespace,
                                 @Advice.Argument(1) BsonDocument command) {


        String database = null;
        String collection = null;
        if (databaseOrMongoNamespace instanceof String) {
            database = (String) databaseOrMongoNamespace;
        } else if (databaseOrMongoNamespace instanceof MongoNamespace) {
            MongoNamespace namespace = (MongoNamespace) databaseOrMongoNamespace;
            database = namespace.getDatabaseName();
            collection = namespace.getCollectionName();
        }

        String cmd = null;
        try {
            cmd =
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
                }
            }
            if (collection == null) {
                BsonValue collectionName = command.get("collection");
                if (collectionName != null && collectionName.isString()) {
                    collection = collectionName.asString().getValue();
                }
            }
        } catch (RuntimeException e) {
            logger.error("Exception while determining MongoDB command and collection", e);
        }


        return helper.startSpan(database, collection, cmd, thiz.getDescription().getServerAddress());
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
