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
import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import software.amazon.awssdk.auth.signer.AwsSignerExecutionAttribute;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.http.ExecutionContext;
import software.amazon.awssdk.core.internal.http.TransformingAsyncResponseHandler;
import software.amazon.awssdk.http.SdkHttpFullRequest;

import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class BaseAsyncClientHandlerInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("software.amazon.awssdk.core.internal.handler.BaseAsyncClientHandler");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("invoke")
            .and(takesArgument(0, named("software.amazon.awssdk.http.SdkHttpFullRequest")))
            .and(takesArgument(3, named("software.amazon.awssdk.core.http.ExecutionContext")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("aws-sdk");
    }


    public static class AdviceClass {
        @SuppressWarnings("rawtypes")
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @Advice.AssignReturned.ToArguments(@Advice.AssignReturned.ToArguments.ToArgument(value = 4))
        public static TransformingAsyncResponseHandler<?> enterInvoke(@Advice.Argument(value = 0) SdkHttpFullRequest sdkHttpFullRequest,
                                                                      @Advice.Argument(value = 2) Object sdkRequestObj,
                                                                      @Advice.Argument(value = 3) ExecutionContext executionContext,
                                                                      @Advice.Argument(value = 4) TransformingAsyncResponseHandler<?> responseHandler) {
            if (sdkRequestObj instanceof SdkRequest) {
                String awsService = executionContext.executionAttributes().getAttribute(AwsSignerExecutionAttribute.SERVICE_NAME);
                Span span = null;
                if ("S3".equalsIgnoreCase(awsService)) {
                    span = S3Helper.getInstance().startSpan((SdkRequest) sdkRequestObj, sdkHttpFullRequest.getUri(), executionContext);
                } else if ("DynamoDb".equalsIgnoreCase(awsService)) {
                    span = DynamoDbHelper.getInstance().startSpan((SdkRequest) sdkRequestObj, sdkHttpFullRequest.getUri(), executionContext);
                }
                if (span != null) {
                    span.withSync(false);
                    return new ResponseHandlerWrapper(responseHandler, span);
                }
            }

            return responseHandler;
        }
    }
}
