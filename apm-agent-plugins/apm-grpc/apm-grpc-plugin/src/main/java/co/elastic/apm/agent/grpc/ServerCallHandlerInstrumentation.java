/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.grpc;

import co.elastic.apm.agent.impl.transaction.Transaction;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments implementations of {@link io.grpc.ServerCallHandler#startCall(ServerCall, Metadata)} in order to start
 * transaction.
 */
public class ServerCallHandlerInstrumentation extends BaseInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        // pre-filtering is used to make this quite fast and limited to gRPC packages
        return hasSuperType(named("io.grpc.ServerCallHandler"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("startCall");
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnter(@Advice.Origin Class<?> clazz,
                                  @Advice.Argument(0) ServerCall<?, ?> serverCall,
                                  @Advice.Argument(1) Metadata headers) {

        return helper.startTransaction(tracer, clazz.getClassLoader(), serverCall, headers);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(@Advice.Thrown @Nullable Throwable thrown,
                               @Advice.Argument(0) ServerCall<?, ?> serverCall,
                               @Advice.Return ServerCall.Listener<?> listener,
                               @Advice.Enter @Nullable Object enterTransaction) {

        if (!(enterTransaction instanceof Transaction)) {
            return;
        }
        Transaction transaction = (Transaction) enterTransaction;
        if (thrown != null) {
            // terminate transaction in case of exception as it won't be stored
            transaction.deactivate().end();
            return;
        }

        helper.registerTransaction(serverCall, listener, transaction);
    }

}
