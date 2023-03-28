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
package co.elastic.apm.agent.websocket;

import co.elastic.apm.agent.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.util.VersionUtils;
import jakarta.websocket.server.ServerEndpoint;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

public class JakartaServerEndpointInstrumentation extends BaseServerEndpointInstrumentation {

    public JakartaServerEndpointInstrumentation(Tracer tracer) {
        super(tracer);
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("websocket", "jakarta-websocket");
    }

    @Override
    protected String getServerEndpointClassName() {
        return "jakarta.websocket.server.ServerEndpoint";
    }

    public static class AdviceClass extends BaseAdvice {

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onMethodEnter(@SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature String signature) {
            String frameworkVersion = VersionUtils.getVersion(ServerEndpoint.class, "jakarta.websocket", "jakarta.websocket-api");
            return startTransactionOrSetTransactionName(signature, "Jakarta WebSocket", frameworkVersion);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onMethodExit(@Advice.Enter @Nullable Object transactionOrNull, @Advice.Thrown @Nullable Throwable t) {
            endTransaction(transactionOrNull, t);
        }
    }
}
