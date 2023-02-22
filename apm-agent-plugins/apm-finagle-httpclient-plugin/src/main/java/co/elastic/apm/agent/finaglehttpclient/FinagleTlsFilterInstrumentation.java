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
package co.elastic.apm.agent.finaglehttpclient;


import co.elastic.apm.agent.sdk.TracerAwareInstrumentation;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakSet;
import com.twitter.finagle.http.Request;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * This instrumentation targets {@link com.twitter.finagle.http.TlsFilter}.
 * If this filter is executed for a request, we assume that it is a TLS request.
 */
@SuppressWarnings("JavadocReference")
public class FinagleTlsFilterInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("com.twitter.finagle.http.TlsFilter");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("apply")
            .and(returns(named("com.twitter.util.Future")))
            .and(takesArguments(2))
            .and(takesArgument(0, named("com.twitter.finagle.http.Request")))
            .and(takesArgument(1, named("com.twitter.finagle.Service")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "finagle-httpclient");
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.finaglehttpclient.FinagleTlsFilterInstrumentation$TlsFilterAdvice";
    }

    public static class TlsFilterAdvice {

        private static final WeakSet<Request> TLS_REQUESTS = WeakConcurrent.buildSet();

        public static boolean isTlsRequest(Request request) {
            return TLS_REQUESTS.contains(request);
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onBeforeExecute(@Nullable @Advice.Argument(0) Request request) {
            if (request == null) {
                return;
            }
            TLS_REQUESTS.add(request);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Nullable @Advice.Argument(0) Request request) {
            if (request == null) {
                return;
            }
            //TODO: this was built under the assumption that this filter is invoked synchronously before the PayloadSizeFilter
            //This assumption very likely doesn't hold 100% of the time, but is deemed good enough for now
            TLS_REQUESTS.remove(request);
        }
    }
}
