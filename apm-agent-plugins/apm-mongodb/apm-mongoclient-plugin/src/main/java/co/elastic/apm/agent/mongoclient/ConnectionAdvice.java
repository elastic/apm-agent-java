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
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.mongodb.MongoHelper;
import com.mongodb.MongoNamespace;
import com.mongodb.connection.Connection;
import net.bytebuddy.asm.Advice;
import org.bson.BsonDocument;

import javax.annotation.Nullable;

public class ConnectionAdvice {

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

        String cmd = helper.getCommandFromBson(command);

        if (collection == null) {
            collection = helper.getCollectionFromBson(cmd, command);
        }

        Span span = helper.startSpan(database, collection, cmd);
        if (span == null) {
            return null;
        }

        helper.setServerAddress(span, thiz.getDescription().getServerAddress());
        return span;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(@Nullable @Advice.Enter Object spanObj, @Advice.Thrown Throwable thrown) {
        if (spanObj instanceof Span) {
            Span span = (Span) spanObj;
            span.deactivate()
                .captureException(thrown)
                .end();
        }
    }

}
