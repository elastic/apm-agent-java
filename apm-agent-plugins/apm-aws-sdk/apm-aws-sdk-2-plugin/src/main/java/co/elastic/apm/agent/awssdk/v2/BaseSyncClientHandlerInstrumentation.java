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
import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.common.JvmRuntimeInfo;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
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

import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class BaseSyncClientHandlerInstrumentation extends TracerAwareInstrumentation {
    //Coretto causes sigsegv crashes when you try to access a throwable if it thinks
    //it went out of scope, which it seems to for the instrumented throwable access
    //package access and non-final so that tests can replace this
    static JvmRuntimeInfo JVM_RUNTIME_INFO = JvmRuntimeInfo.ofCurrentVM();

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
                span.activate();
            }

            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
        public static void exitDoExecute(@Advice.Argument(value = 0) ClientExecutionParams clientExecutionParams,
                                         @Advice.Argument(value = 1) ExecutionContext executionContext,
                                         @Nullable @Advice.Enter Object spanObj,
                                         @Nullable @Advice.Thrown Throwable thrown,
                                         @Nullable @Advice.Return Object sdkResponse,
                                         @Advice.This Object thiz) {
            String awsService = executionContext.executionAttributes().getAttribute(AwsSignerExecutionAttribute.SERVICE_NAME);
            SdkRequest sdkRequest = clientExecutionParams.getInput();
            if (spanObj instanceof Span<?>) {
                Span<?> span = (Span<?>) spanObj;
                span.deactivate();
                if (thrown != null) {
                    if (JVM_RUNTIME_INFO.isCoretto() && JVM_RUNTIME_INFO.getMajorVersion() > 16) {
                        span.captureException(RedactedException.getInstance(thiz.getClass().getName()));
                    } else {
                        span.captureException(thrown);
                    }
                    span.withOutcome(Outcome.FAILURE);
                } else {
                    span.withOutcome(Outcome.SUCCESS);
                }

                if ("Sqs".equalsIgnoreCase(awsService) && sdkResponse instanceof SdkResponse) {
                    SQSHelper.getInstance().handleReceivedMessages(span, sdkRequest, (SdkResponse) sdkResponse);
                }

                span.end();
            }

            if ("Sqs".equalsIgnoreCase(awsService) && sdkResponse instanceof SdkResponse) {
                MessageListWrapper.registerWrapperListForResponse(sdkRequest, (SdkResponse) sdkResponse, SQSHelper.getInstance().getTracer());
            }
        }
    }

    static class RedactedException extends Exception {
        //package access and non-final so that tests can access this
        static ConcurrentMap<String,RedactedException> Exceptions = new ConcurrentHashMap<>();

        private RedactedException() {
            super("Unable to provide details of the error");
        }

        static RedactedException getInstance(String classname) {
            if (!Exceptions.containsKey(classname)) {
                // race but if we create extra instances it doesn't matter apart from a little extra overhead and garbage
                RedactedException newException = new RedactedException();
                StackTraceElement[] stack = newException.getStackTrace();
                int stackElementToStartAt = 0;
                for (; stackElementToStartAt < stack.length; stackElementToStartAt++) {
                    if (stack[stackElementToStartAt].getClassName().equals(classname)) {
                        break;
                    }
                }
                if (stackElementToStartAt > 0 && stackElementToStartAt < stack.length) {
                    StackTraceElement[] newstack = new StackTraceElement[stack.length-stackElementToStartAt];
                    System.arraycopy(stack, stackElementToStartAt, newstack, 0, newstack.length);
                    newException.setStackTrace(newstack);
                }
                Exceptions.putIfAbsent(classname, newException);
            }
            return Exceptions.get(classname);
        }
    }
}
