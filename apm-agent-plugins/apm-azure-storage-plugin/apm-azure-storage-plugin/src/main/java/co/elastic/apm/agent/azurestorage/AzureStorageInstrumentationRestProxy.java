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
package co.elastic.apm.agent.azurestorage;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import com.azure.core.http.HttpRequest;
import com.azure.core.util.Context;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class AzureStorageInstrumentationRestProxy extends TracerAwareInstrumentation {
    static String[] BLOB_HOSTS = new String[] {
        ".blob.core.windows.net",
        ".blob.core.usgovcloudapi.net",
        ".blob.core.chinacloudapi.cn",
        ".blob.core.cloudapi.de"
    };
    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return ElementMatchers.nameContains("RestProxy");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("com.azure.core.http.rest.RestProxy");
    }

    /**
     */
    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("send")
            .and(takesArguments(2))
            .and(takesArgument(0, named("com.azure.core.http.HttpRequest")))
            .and(takesArgument(1, named("com.azure.core.util.Context")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("azurestorage");
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.azurestorage.AzureStorageInstrumentationRestProxy$AzureStorageAdvice";
    }

    public static class AzureStorageAdvice {
        private static final AzureStorageHelper azureHelper = new AzureStorageHelper(GlobalTracer.get());

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.Argument(0) HttpRequest request,@Advice.Argument(1) Context context) {
            URL url = request.getUrl();
            boolean matches = false;
            if (url != null && url.getHost() != null) {
                String host = url.getHost().toLowerCase();
                for (String checkHost : BLOB_HOSTS) {
                    if (host.indexOf(checkHost) != 0) {
                        matches = true;
                        break;
                    }
                }
            }
            if (!matches) return null;
            SpanTrackerHolder spanTrackerHolder = SpanTrackerHolder.getSpanTrackHolder();
            Map<String, String> requestHeaderMap = request.getHeaders().toMap();
            String httpMethod = request.getHttpMethod().toString();
            Span span = azureHelper.startAzureStorageSpan(httpMethod, url, requestHeaderMap);
            spanTrackerHolder.setSpan(span);
            return spanTrackerHolder;
        }

        @Nullable
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static Object onExit(@Advice.Thrown @Nullable Throwable thrown,
                                  @Advice.Return @Nullable Mono<?> returnValue,
                                  @Nullable @Advice.Enter Object spanTrackerHolderObj) {
            if (thrown != null || returnValue == null) {
                // in case of thrown exception, we don't need to wrap to end transaction
                return returnValue;
            }
            SpanTrackerHolder spanTrackerHolder = (SpanTrackerHolder) spanTrackerHolderObj;
            if (spanTrackerHolder != null && !spanTrackerHolder.isStorageEntrypointCreated() && spanTrackerHolder.getSpan() != null) {
                // If the spanTrackHolder not created on StorageEntrypoint, finish span
                spanTrackerHolder.getSpan().captureException(thrown).deactivate();
                spanTrackerHolder.getSpan().end();
                SpanTrackerHolder.removeSpanTrackHolder();
            }
            return returnValue;
        }
    }
}
