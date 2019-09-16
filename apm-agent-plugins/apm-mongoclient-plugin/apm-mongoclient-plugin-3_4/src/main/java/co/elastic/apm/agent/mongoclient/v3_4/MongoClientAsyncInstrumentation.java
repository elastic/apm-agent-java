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
package co.elastic.apm.agent.mongoclient.v3_4;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.mongoclient.MongoClientInstrumentation;
import co.elastic.apm.agent.mongoclient.MongoClientInstrumentationHelper;
import com.mongodb.event.CommandEvent;
import com.mongodb.event.CommandListener;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class MongoClientAsyncInstrumentation extends MongoClientInstrumentation {

    public MongoClientAsyncInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    public static class MongoClientAsyncAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onBeforeSet(@Advice.Argument(value = 0, readOnly = false) CommandListener commandListener,
                                       @Advice.Local("span") Span span,
                                       @Advice.Local("helper") MongoClientInstrumentationHelper<CommandEvent, CommandListener> helper) {
            helper = mongoClientInstrHelperManager.getForClassLoaderOfClass(CommandEvent.class);

            if (helper != null) {
                span = helper.createClientSpan();
                if (span != null) {
                    commandListener = helper.<CommandListener>wrapCommandListener(commandListener, span);
                }
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onAfterExecute(@Advice.Argument(value = 0) CommandListener commandListener,
                                           @Advice.Local("span") @Nullable Span span,
                                           @Advice.Local("helper") @Nullable MongoClientInstrumentationHelper<CommandEvent, CommandListener> helper,
                                           @Advice.Thrown @Nullable Throwable t) {
            if (span != null) {
                // Deactivate in this thread. Span will be ended and reported by the listener
                span.deactivate();
            }
        }

    }

    @Override
    public Class<?> getAdviceClass() {
        return MongoClientAsyncAdvice.class;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("com.mongodb.connection.Protocol"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("setCommandListener");
    }

}
