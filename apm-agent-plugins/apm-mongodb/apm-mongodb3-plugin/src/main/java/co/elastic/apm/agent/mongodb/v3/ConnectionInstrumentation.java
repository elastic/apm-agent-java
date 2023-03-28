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
package co.elastic.apm.agent.mongodb.v3;

import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.mongodb.MongoHelper;
import com.mongodb.MongoNamespace;
import com.mongodb.ServerAddress;
import com.mongodb.connection.Connection;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments methods which were available up until 3.5.0.
 * In more recent 3.x versions, everything is funneled through {@link com.mongodb.connection.Connection#command}.
 * <ul>
 *   <li>{@link com.mongodb.connection.Connection#insert}</li>
 *   <li>{@link com.mongodb.connection.Connection#update}</li>
 *   <li>{@link com.mongodb.connection.Connection#delete}</li>
 *   <li>{@link com.mongodb.connection.Connection#query}</li>
 *   <li>{@link com.mongodb.connection.Connection#getMore}</li>
 *   <li>com.mongodb.connection.Connection#insertCommand</li>
 *   <li>com.mongodb.connection.Connection#updateCommand</li>
 *   <li>com.mongodb.connection.Connection#deleteCommand</li>
 * </ul>
 */
public class ConnectionInstrumentation extends Mongo3Instrumentation {

    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("insert")
            .or(named("delete"))
            .or(named("update"))
            .or(named("query"))
            .or(named("getMore"))
            .or(named("insertCommand"))
            .or(named("updateCommand"))
            .or(named("deleteCommand"))
            .and(isPublic())
            .and(takesArgument(0, named("com.mongodb.MongoNamespace")));
    }

    public static class AdviceClass {

        private static final MongoHelper helper = new MongoHelper();

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.This Connection thiz,
                                     @Advice.Argument(0) MongoNamespace namespace,
                                     @Advice.Origin("#m") String methodName) {

            String command = methodName;
            if (methodName.equals("query")) {
                // if the method name is query, that corresponds to the find command
                command = "find";
            }
            int indexOfCommand = command.indexOf("Command");
            if (indexOfCommand > 0) {
                command = command.substring(0, indexOfCommand);
            }

            ServerAddress address = thiz.getDescription().getServerAddress();
            return helper.startSpan(
                namespace.getDatabaseName(),
                namespace.getCollectionName(),
                command,
                address.getHost(), address.getPort(), null);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Nullable @Advice.Enter Object spanObj, @Advice.Thrown Throwable thrown) {
            if (spanObj instanceof Span<?>) {
                Span<?> span = (Span<?>) spanObj;
                span.deactivate().captureException(thrown);
                span.end();
            }
        }
    }
}
