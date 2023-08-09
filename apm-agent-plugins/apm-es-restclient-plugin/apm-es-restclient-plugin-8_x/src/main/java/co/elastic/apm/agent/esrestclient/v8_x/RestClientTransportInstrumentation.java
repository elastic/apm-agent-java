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
import co.elastic.clients.transport.TransportOptions;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.elasticsearch.client.Request;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Instruments {@link co.elastic.clients.transport.rest_client.RestClientTransport#prepareLowLevelRequest(Object, Endpoint, TransportOptions)}.
 */
@SuppressWarnings("JavadocReference")
public class RestClientTransportInstrumentation extends ElasticsearchRestClientInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.clients.transport.rest_client.RestClientTransport");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("prepareLowLevelRequest")
            .and(takesArguments(3))
            .and(takesArgument(1, named("co.elastic.clients.transport.Endpoint")))
            .and(returns(named("org.elasticsearch.client.Request")));
    }

    @Override
    public String getAdviceClassName() {
        return getClass().getName() + "$RestClientTransportAdvice";
    }

    public static class RestClientTransportAdvice {

        private static final ElasticsearchRestClientInstrumentationHelper helper = ElasticsearchRestClientInstrumentationHelper.get();


        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void onPrepareLowLevelRequest(@Advice.Argument(1) Endpoint<?, ?, ?> endpoint, @Advice.Return Request request) {
            helper.registerEndpointId(request, endpoint.id());
        }

    }
}
