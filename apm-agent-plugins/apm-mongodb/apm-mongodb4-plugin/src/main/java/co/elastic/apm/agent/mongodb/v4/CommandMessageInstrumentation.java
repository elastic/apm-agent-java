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

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.mongodb.MongoHelper;
import com.mongodb.MongoNamespace;
import com.mongodb.internal.connection.CommandMessage;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.bson.BsonDocument;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments {@link CommandMessage} constructors
 */
public class CommandMessageInstrumentation extends Mongo4Instrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("com.mongodb.internal.connection.CommandMessage");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isConstructor()
            .and(takesArgument(0, named("com.mongodb.MongoNamespace")))
            .and(takesArgument(1, named("org.bson.BsonDocument")));
    }

    @Override
    public String getAdviceClassName() {
        return CommandMessageInstrumentation.class.getCanonicalName() + "$ConstructorAdvice";
    }

    public static class ConstructorAdvice {

        private static final Tracer tracer = GlobalTracer.get();
        private static final MongoHelper helper = new MongoHelper(tracer);

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.Argument(0) MongoNamespace namespace,
                                     @Advice.Argument(1) BsonDocument command) {

            if (null == tracer.getActive() || namespace.getDatabaseName().equals("admin")) {
                return null;
            }

            String cmd = helper.getCommandFromBson(command);

            String collection = namespace.getCollectionName();
            if (MongoNamespace.COMMAND_COLLECTION_NAME.equals(collection)) {
                collection = helper.getCollectionFromBson(cmd, command);
            }

            // span has to remain active during constructor to prevent nested constructor calls to create another
            return helper.startSpan(namespace.getDatabaseName(), collection, cmd);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void onExit(@Advice.This CommandMessage commandMessage,
                                  @Advice.Enter Object enterSpan) {

            if (enterSpan instanceof Span) {
                Span span = (Span) enterSpan;
                Mongo4Storage.inFlightSpans.put(commandMessage, span);
                span.deactivate();
            }

        }
    }
}
