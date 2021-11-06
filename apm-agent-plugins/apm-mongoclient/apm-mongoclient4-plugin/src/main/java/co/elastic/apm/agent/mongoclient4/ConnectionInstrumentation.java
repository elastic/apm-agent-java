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
package co.elastic.apm.agent.mongoclient4;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import com.mongodb.MongoNamespace;
import com.mongodb.ServerAddress;
import com.mongodb.internal.connection.Connection;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments methods which were available up until 3.5.0.
 * In more recent versions, everything is funneled through {@link com.mongodb.internal.connection.Connection#command}.
 * <ul>
 *   <li>{@link com.mongodb.internal.connection.Connection#insert}</li>
 *   <li>{@link com.mongodb.internal.connection.Connection#update}</li>
 *   <li>{@link com.mongodb.internal.connection.Connection#delete}</li>
 *   <li>{@link com.mongodb.internal.connection.Connection#query}</li>
 *   <li>{@link com.mongodb.internal.connection.Connection#getMore}</li>
 *   <li>com.mongodb.connection.Connection#insertCommand</li>
 *   <li>com.mongodb.connection.Connection#updateCommand</li>
 *   <li>com.mongodb.connection.Connection#deleteCommand</li>
 * </ul>
 */
public class ConnectionInstrumentation extends MongoDriverInstrumentation {

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return nameStartsWith("insert")
            .or(nameStartsWith("update"))
            .or(nameStartsWith("delete"))
            .or(nameStartsWith("query"))
            .or(nameStartsWith("getMore"))
            .and(isPublic())
            .and(takesArgument(0, named("com.mongodb.MongoNamespace")));
    }

    public static class AdviceClass {
        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.This Connection thiz,
                                   @Advice.Argument(0) MongoNamespace namespace,
                                   @Advice.Origin("#m") String methodName) {
            Span span = null;
            final AbstractSpan<?> activeSpan = tracer.getActive();
            if (activeSpan != null && !activeSpan.isExit()) {
                span = activeSpan.createExitSpan();
            }

            if (span == null) {
                return null;
            }

            span.withType("db").withSubtype("mongodb")
                .getContext().getDb().withType("mongodb");
            span.getContext().getDestination().getService()
                .withName("mongodb").withResource("mongodb").withType("db");
            ServerAddress serverAddress = thiz.getDescription().getServerAddress();
            span.getContext().getDestination()
                .withAddress(serverAddress.getHost())
                .withPort(serverAddress.getPort());

            String command = methodName;
            if (methodName.equals("query")) {
                // if the method name is query, that corresponds to the find command
                command = "find";
            }
            span.withAction(command);
            StringBuilder spanName = span.getAndOverrideName(AbstractSpan.PRIO_DEFAULT);
            if (spanName != null) {
                int indexOfCommand = command.indexOf("Command");
                spanName.append(namespace.getDatabaseName())
                    .append(".").append(namespace.getCollectionName())
                    .append(".").append(command, 0, indexOfCommand > 0 ? indexOfCommand : command.length());
            }
            span.activate();
            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Nullable @Advice.Enter Object spanObj, @Advice.Thrown Throwable thrown) {
            if (spanObj instanceof Span) {
                Span span = (Span) spanObj;
                span.deactivate().captureException(thrown);
                span.end();
            }
        }
    }
}
