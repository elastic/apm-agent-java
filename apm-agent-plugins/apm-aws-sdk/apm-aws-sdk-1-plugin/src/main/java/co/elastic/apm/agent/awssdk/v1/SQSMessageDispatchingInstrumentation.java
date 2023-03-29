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
package co.elastic.apm.agent.awssdk.v1;

import co.elastic.apm.agent.awssdk.common.AbstractAwsSdkInstrumentation;
import co.elastic.apm.agent.awssdk.v1.helper.SQSHelper;
import co.elastic.apm.agent.awssdk.v1.helper.SdkV1DataSource;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.sdk.state.CallDepth;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public abstract class SQSMessageDispatchingInstrumentation extends AbstractAwsSdkInstrumentation {

    protected static final CallDepth jmsReceiveMessageCallDepth = CallDepth.get(AmazonSQSMessagingClientWrapperInstrumentation.class);

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("com.amazonaws.services.sqs.AmazonSQSClient");
    }

    protected static void endSpan(@Nullable Object spanObj, @Nullable Throwable thrown) {
        if (spanObj instanceof Span<?>) {
            Span<?> span = (Span<?>) spanObj;
            span.deactivate();
            if (thrown != null) {
                span.captureException(thrown);
                span.withOutcome(Outcome.FAILURE);
            } else {
                span.withOutcome(Outcome.SUCCESS);
            }
            span.end();
        }
    }

    /**
     * Instruments the sendMessage method.
     * Do the trace context propagation in this instrumentation.
     */
    public static class SendMessageInstrumentation extends SQSMessageDispatchingInstrumentation {
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("executeSendMessage").and(takesArgument(0, named("com.amazonaws.services.sqs.model.SendMessageRequest")))
                .or(named("executeSendMessageBatch").and(takesArgument(0, named("com.amazonaws.services.sqs.model.SendMessageBatchRequest"))));
        }

        public static class AdviceClass {

            @Nullable
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Object enterSendMessage(@Advice.Argument(value = 0) AmazonWebServiceRequest request) {
                String queueUrl = null;
                if (request instanceof SendMessageRequest) {
                    queueUrl = ((SendMessageRequest) request).getQueueUrl();
                } else if (request instanceof SendMessageBatchRequest) {
                    queueUrl = ((SendMessageBatchRequest) request).getQueueUrl();
                }

                String queueName = SdkV1DataSource.getInstance().getQueueNameFromQueueUrl(queueUrl);

                if (queueName == null) {
                    return null;
                }

                Span<?> span = SQSHelper.getInstance().createSpan(queueName);

                if (span != null) {
                    SQSHelper.getInstance().propagateContext(span, request);
                    span.activate();
                    SdkV1DataSource.getInstance().putLookupValue(request, queueName);
                }

                return span;
            }

            @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
            public static void exitSendMessage(@Nullable @Advice.Enter Object spanObj,
                                               @Nullable @Advice.Thrown Throwable thrown) {
                endSpan(spanObj, thrown);
            }
        }
    }

    public static class SQSReceiveMessageInstrumentation extends SQSMessageDispatchingInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("executeReceiveMessage")
                .and(takesArgument(0, named("com.amazonaws.services.sqs.model.ReceiveMessageRequest")));
        }

        public static class AdviceClass {
            @Nullable
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Object enterReceiveMessage(@Advice.Argument(value = 0) ReceiveMessageRequest receiveMessageRequest) {
                String queueName = SdkV1DataSource.getInstance().getQueueNameFromQueueUrl(receiveMessageRequest.getQueueUrl());
                Span<?> span = SQSHelper.getInstance().createSpan(queueName);

                if (span != null) {
                    span.activate();
                    SdkV1DataSource.getInstance().putLookupValue(receiveMessageRequest, queueName);
                }

                SQSHelper.getInstance().setMessageAttributeNames(receiveMessageRequest);

                return span;
            }

            @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
            @Advice.AssignReturned.ToReturned
            public static ReceiveMessageResult exitReceiveMessage(@Nullable @Advice.Enter Object spanObj,
                                                                  @Advice.Argument(value = 0) ReceiveMessageRequest receiveMessageRequest,
                                                                  @Advice.Return ReceiveMessageResult result,
                                                                  @Nullable @Advice.Thrown Throwable thrown) {
                if (spanObj instanceof Span<?>) {
                    SQSHelper.getInstance().handleReceivedMessages((Span<?>) spanObj, receiveMessageRequest.getQueueUrl(), result.getMessages());
                }
                endSpan(spanObj, thrown);

                ReceiveMessageResult returnedResult = result;
                if (!jmsReceiveMessageCallDepth.isNestedCallAndIncrement()) {
                    // Wrap result only if the messages are NOT received as part of JMS.
                    returnedResult = SQSHelper.getInstance().wrapResult(receiveMessageRequest, result);
                }
                jmsReceiveMessageCallDepth.decrement();

                return returnedResult;
            }
        }
    }

    public static class AmazonSQSMessagingClientWrapperInstrumentation extends SQSMessageDispatchingInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("com.amazon.sqs.javamessaging.AmazonSQSMessagingClientWrapper");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("receiveMessage")
                .and(takesArgument(0, named("com.amazonaws.services.sqs.model.ReceiveMessageRequest")));
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void enterReceiveMessage() {
                jmsReceiveMessageCallDepth.increment();
            }

            @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
            public static void exitReceiveMessage() {
                jmsReceiveMessageCallDepth.decrement();
            }
        }
    }


}
