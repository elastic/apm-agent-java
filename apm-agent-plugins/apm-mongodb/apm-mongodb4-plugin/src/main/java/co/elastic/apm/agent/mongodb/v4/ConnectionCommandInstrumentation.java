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
package co.elastic.apm.agent.mongodb.v4;

import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.mongodb.MongoHelper;
import com.mongodb.ServerAddress;
import com.mongodb.internal.connection.Connection;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.bson.BsonDocument;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * {@link Connection#command}
 */
public class ConnectionCommandInstrumentation extends Mongo4Instrumentation {

    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("com.mongodb.internal.connection")
            .and(hasSuperType(named("com.mongodb.internal.connection.Connection")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("command")
            .and(isPublic())
            .and(takesArgument(0, is(String.class)))
            .and(takesArgument(1, named("org.bson.BsonDocument")));
    }

    public static class AdviceClass {

        private static final MongoHelper helper = new MongoHelper();

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.This Connection thiz,
                                     @Advice.Argument(0) String database,
                                     @Advice.Argument(1) BsonDocument command) {

            String cmd = helper.getCommandFromBson(command);
            String collection = helper.getCollectionFromBson(cmd, command);

            ServerAddress address = thiz.getDescription().getServerAddress();
            return helper.startSpan(database, collection, cmd, address.getHost(), address.getPort(), command);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Nullable @Advice.Enter Object spanObj, @Advice.Thrown Throwable thrown) {
            if (spanObj instanceof Span<?>) {
                Span<?> span = (Span<?>) spanObj;
                span.deactivate()
                    .captureException(thrown)
                    .end();
            }
        }
    }
}
