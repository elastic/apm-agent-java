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
import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.tracer.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import software.amazon.awssdk.auth.signer.AwsSignerExecutionAttribute;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.client.config.SdkClientConfiguration;
import software.amazon.awssdk.core.client.config.SdkClientOption;
import software.amazon.awssdk.core.client.handler.ClientExecutionParams;
import software.amazon.awssdk.core.http.ExecutionContext;
import software.amazon.awssdk.core.internal.http.TransformingAsyncResponseHandler;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class BaseAsyncClientHandlerInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("software.amazon.awssdk.core.internal.handler.BaseAsyncClientHandler");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return nameStartsWith("doExecute")
            .and(takesArgument(0, named("software.amazon.awssdk.core.client.handler.ClientExecutionParams")))
            .and(takesArgument(1, named("software.amazon.awssdk.core.http.ExecutionContext")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("aws-sdk");
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    public static class AdviceClass {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @Advice.AssignReturned.ToArguments(@Advice.AssignReturned.ToArguments.ToArgument(value = 2))
        public static TransformingAsyncResponseHandler<?> enterDoExecute(@Advice.Argument(value = 0) ClientExecutionParams clientExecutionParams,
                                                                         @Advice.Argument(value = 1) ExecutionContext executionContext,
                                                                         @Advice.Argument(value = 2) TransformingAsyncResponseHandler<?> responseHandler,
                                                                         @Advice.FieldValue("clientConfiguration") SdkClientConfiguration clientConfiguration) {
            String awsService = executionContext.executionAttributes().getAttribute(AwsSignerExecutionAttribute.SERVICE_NAME);
            SdkRequest sdkRequest = clientExecutionParams.getInput();
            URI uri = clientConfiguration.option(SdkClientOption.ENDPOINT);
            Span<?> span = null;
            if ("S3".equalsIgnoreCase(awsService)) {
                span = S3Helper.getInstance().startSpan(sdkRequest, uri, executionContext);
            } else if ("DynamoDb".equalsIgnoreCase(awsService)) {
                span = DynamoDbHelper.getInstance().startSpan(sdkRequest, uri, executionContext);
            } else if ("Sqs".equalsIgnoreCase(awsService)) {
                span = SQSHelper.getInstance().startSpan(sdkRequest, uri, executionContext);
                SQSHelper.getInstance().modifyRequestObject(span, clientExecutionParams, executionContext);
            }

            if (span != null) {
                span.withSync(false);
            }

            return new ResponseHandlerWrapper(awsService, responseHandler, sdkRequest, span);
        }
    }
}
