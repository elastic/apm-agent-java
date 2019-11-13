/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.mongoclient;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import com.mongodb.MongoNamespace;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments methods which were available up until 3.5.0.
 * In more recent versions, everything is funneled through {@link com.mongodb.connection.Connection#command}.
 * <ul>
 *   <li>{@link com.mongodb.connection.Connection#insert}</li>
 *   <li>{@link com.mongodb.connection.Connection#update}</li>
 *   <li>{@link com.mongodb.connection.Connection#delete}</li>
 *   <li>{@link com.mongodb.connection.Connection#query}</li>
 *   <li>{@link com.mongodb.connection.Connection#insertCommand}</li>
 *   <li>{@link com.mongodb.connection.Connection#updateCommand}</li>
 *   <li>{@link com.mongodb.connection.Connection#deleteCommand}</li>
 * </ul>
 */
public class ConnectionInstrumentation extends MongoClientInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("com.mongodb.")
            .and(hasSuperType(named("com.mongodb.connection.Connection")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return nameStartsWith("insert")
            .or(nameStartsWith("update"))
            .or(nameStartsWith("delete"))
            .or(nameStartsWith("query"))
            .and(isPublic())
            .and(takesArgument(0, named("com.mongodb.MongoNamespace")));
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Span onEnter(@Advice.Argument(0) MongoNamespace namespace, @Advice.Origin("#m") String methodName) {
        Span span = ElasticApmInstrumentation.createExitSpan();

        if (span == null) {
            return null;
        }

        span.withType("db").withSubtype("mongodb")
            .getContext().getDb().withType("mongodb");
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

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(@Nullable @Advice.Enter Span span, @Advice.Thrown Throwable thrown) {
        if (span != null) {
            span.deactivate().captureException(thrown);
            span.end();
        }
    }
}
