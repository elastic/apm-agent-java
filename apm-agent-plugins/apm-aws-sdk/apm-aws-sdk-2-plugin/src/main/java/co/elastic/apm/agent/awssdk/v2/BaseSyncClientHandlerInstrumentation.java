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

import co.elastic.apm.agent.awssdk.common.IAwsSdkDataSource;
import co.elastic.apm.agent.awssdk.v2.helper.DynamoDbHelper;
import co.elastic.apm.agent.awssdk.v2.helper.S3Helper;
import co.elastic.apm.agent.awssdk.v2.helper.SQSHelper;
import co.elastic.apm.agent.awssdk.v2.helper.SdkV2DataSource;
import co.elastic.apm.agent.awssdk.v2.helper.sqs.wrapper.MessageListWrapper;
import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
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
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class BaseSyncClientHandlerInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("software.amazon.awssdk.core.internal.handler.BaseSyncClientHandler");
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


    @SuppressWarnings("rawtypes")
    public static class AdviceClass {

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object enterDoExecute(@Advice.Argument(value = 0) ClientExecutionParams clientExecutionParams,
                                            @Advice.Argument(value = 1) ExecutionContext executionContext,
                                            @Advice.FieldValue("clientConfiguration") SdkClientConfiguration clientConfiguration) {
            String awsService = executionContext.executionAttributes().getAttribute(AwsSignerExecutionAttribute.SERVICE_NAME);
            SdkRequest sdkRequest = clientExecutionParams.getInput();
            URI uri = clientConfiguration.option(SdkClientOption.ENDPOINT);
            Span span = null;
            if ("S3".equalsIgnoreCase(awsService)) {
                span = S3Helper.getInstance().startSpan(sdkRequest, uri, executionContext);
            } else if ("DynamoDb".equalsIgnoreCase(awsService)) {
                span = DynamoDbHelper.getInstance().startSpan(sdkRequest, uri, executionContext);
            } else if ("Sqs".equalsIgnoreCase(awsService)) {
                span = SQSHelper.getInstance().startSpan(sdkRequest, uri, executionContext);
                SQSHelper.getInstance().modifyRequestObject(span, clientExecutionParams, executionContext);
            }

            if (span != null) {
                span.activate();
            }

            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
        public static void exitDoExecute(@Advice.Argument(value = 0) ClientExecutionParams clientExecutionParams,
                                         @Nullable @Advice.Enter Object spanObj,
                                         @Nullable @Advice.Thrown Throwable thrown,
                                         @Nullable @Advice.Return Object response) {
            SdkRequest sdkRequest = clientExecutionParams.getInput();
            if (spanObj instanceof Span) {
                Span span = (Span) spanObj;
                span.deactivate();
                if (thrown != null) {
                    span.captureException(thrown);
                    span.withOutcome(Outcome.FAILURE);
                } else {
                    span.withOutcome(Outcome.SUCCESS);
                }

                if (response instanceof ReceiveMessageResponse && sdkRequest instanceof ReceiveMessageRequest) {
                    SQSHelper.getInstance().handleReceivedMessages(span, ((ReceiveMessageRequest) sdkRequest).queueUrl(), ((ReceiveMessageResponse) response).messages());
                }
                span.end();
            }

            if (response instanceof ReceiveMessageResponse && sdkRequest instanceof ReceiveMessageRequest) {
                MessageListWrapper.registerWrapperListForResponse((ReceiveMessageResponse) response,
                    SdkV2DataSource.getInstance().getFieldValue(IAwsSdkDataSource.QUEUE_NAME_FIELD, sdkRequest),
                    SQSHelper.getInstance().getTracer());
            }
        }
    }
}
