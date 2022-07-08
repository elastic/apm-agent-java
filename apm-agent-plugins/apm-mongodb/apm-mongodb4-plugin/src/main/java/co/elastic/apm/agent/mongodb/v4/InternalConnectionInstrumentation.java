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
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.mongodb.MongoHelper;
import com.mongodb.internal.connection.CommandMessage;
import com.mongodb.internal.connection.InternalConnection;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments {@link com.mongodb.internal.connection.InternalConnection#sendAndReceive}
 */
public class InternalConnectionInstrumentation extends Mongo4Instrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("com.mongodb.")
            .and(hasSuperType(named("com.mongodb.internal.connection.InternalConnection")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("sendAndReceive").and(takesArgument(0, named("com.mongodb.internal.connection.CommandMessage")));
    }

    public static class AdviceClass {

        private static final MongoHelper helper = new MongoHelper(GlobalTracer.get());

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.This InternalConnection thiz,
                                     @Advice.Argument(0) CommandMessage command) {

            Span span = Mongo4Storage.inFlightSpans.remove(command);
            if (span == null) {
                return null;
            }

            helper.setServerAddress(span, thiz.getDescription().getServerAddress());

            return span.activate();
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Advice.Argument(0) CommandMessage command,
                                  @Advice.Enter @Nullable Object spanObj,
                                  @Advice.Thrown @Nullable Throwable thrown) {

            if (spanObj instanceof Span) {
                Span span = (Span) spanObj;
                span.captureException(thrown)
                    .deactivate()
                    .end();
            }
        }
    }

    // TODO sendAndReceiveAsync

}
