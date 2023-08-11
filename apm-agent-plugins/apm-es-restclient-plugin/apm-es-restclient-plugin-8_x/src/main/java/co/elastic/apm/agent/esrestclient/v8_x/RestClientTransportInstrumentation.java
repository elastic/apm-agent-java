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
package co.elastic.apm.agent.esrestclient.v8_x;

import co.elastic.apm.agent.esrestclient.ElasticsearchRestClientInstrumentation;
import co.elastic.apm.agent.esrestclient.ElasticsearchRestClientInstrumentationHelper;
import co.elastic.clients.transport.Endpoint;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * Instruments:
 * <ul>
 *     <li>{@link co.elastic.clients.transport.rest_client.RestClientTransport#performRequest}</li>
 *     <li>{@link co.elastic.clients.transport.rest_client.RestClientTransport#performRequestAsync} </li>
 * </ul>
 * To store the current endpoint ID in a thread-local storage
 */
public class RestClientTransportInstrumentation extends ElasticsearchRestClientInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.clients.transport.rest_client.RestClientTransport") // 8.x up to 8.8.x
            .or(named("co.elastic.clients.transport.ElasticsearchTransportBase")); // 8.9.0+
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("performRequest")
            .or(named("performRequestAsync"))
            .and(takesArguments(3))
            .and(takesArgument(1, named("co.elastic.clients.transport.Endpoint")));
    }

    @Override
    public String getAdviceClassName() {
        return getClass().getName() + "$RestClientTransportAdvice";
    }

    public static class RestClientTransportAdvice {

        private static final ElasticsearchRestClientInstrumentationHelper helper = ElasticsearchRestClientInstrumentationHelper.get();

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onEnter(@Advice.Argument(1) Endpoint<?, ?, ?> endpoint) {
            helper.setCurrentEndpoint(endpoint.id());
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit() {
            helper.clearCurrentEndpoint();
        }

    }
}
