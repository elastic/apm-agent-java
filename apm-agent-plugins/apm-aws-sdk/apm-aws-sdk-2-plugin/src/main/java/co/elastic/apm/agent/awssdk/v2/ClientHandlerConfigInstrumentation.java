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
package co.elastic.apm.agent.awssdk.v2;

import co.elastic.apm.agent.awssdk.v2.helper.DynamoDbHelper;
import co.elastic.apm.agent.awssdk.v2.helper.S3Helper;
import co.elastic.apm.agent.awssdk.v2.helper.SQSHelper;
import co.elastic.apm.agent.awssdk.v2.helper.sqs.wrapper.MessageListWrapper;
import co.elastic.apm.agent.common.JvmRuntimeInfo;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import software.amazon.awssdk.auth.signer.AwsSignerExecutionAttribute;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.client.config.SdkClientConfiguration;
import software.amazon.awssdk.core.client.config.SdkClientOption;
import software.amazon.awssdk.core.client.handler.ClientExecutionParams;
import software.amazon.awssdk.core.http.ExecutionContext;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class ClientHandlerConfigInstrumentation extends ElasticApmInstrumentation {


    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("software.amazon.awssdk.core.internal.handler.BaseSyncClientHandler")
            .or(named("software.amazon.awssdk.core.internal.handler.BaseAsyncClientHandler"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isConstructor()
            .and(takesArgument(0, named("software.amazon.awssdk.core.client.config.SdkClientConfiguration")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("aws-sdk");
    }


    @SuppressWarnings("rawtypes")
    public static class AdviceClass {

        private static final WeakMap<Object, SdkClientConfiguration> configMap = WeakConcurrent.buildMap();

        @Nullable
        public static SdkClientConfiguration getConfig(Object handler) {
            return configMap.get(handler);
        }


        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void exit(@Advice.This Object handler, @Advice.Argument(value = 0) SdkClientConfiguration config) {
            configMap.put(handler, config);
        }

    }
}
