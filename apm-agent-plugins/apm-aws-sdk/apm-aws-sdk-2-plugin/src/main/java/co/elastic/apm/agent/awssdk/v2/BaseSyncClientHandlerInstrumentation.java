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
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import software.amazon.awssdk.auth.signer.AwsSignerExecutionAttribute;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.http.ExecutionContext;
import software.amazon.awssdk.http.SdkHttpFullRequest;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class BaseSyncClientHandlerInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("software.amazon.awssdk.core.internal.handler.BaseSyncClientHandler");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("invoke")
            .and(takesArgument(0, named("software.amazon.awssdk.http.SdkHttpFullRequest")))
            .and(takesArgument(1, named("software.amazon.awssdk.core.SdkRequest")))
            .and(takesArgument(2, named("software.amazon.awssdk.core.http.ExecutionContext")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("aws-sdk");
    }


    public static class AdviceClass {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @Nullable
        public static Object enterInvoke(@Advice.Argument(value = 0) SdkHttpFullRequest sdkHttpFullRequest,
                                         @Advice.Argument(value = 1) SdkRequest sdkRequest,
                                         @Advice.Argument(value = 2) ExecutionContext executionContext) {
            String awsService = executionContext.executionAttributes().getAttribute(AwsSignerExecutionAttribute.SERVICE_NAME);
            Span span = null;
            if ("S3".equalsIgnoreCase(awsService)) {
                span = S3Helper.getInstance().startSpan(sdkRequest, sdkHttpFullRequest.getUri(), executionContext);
            } else if ("DynamoDb".equalsIgnoreCase(awsService)) {
                span = DynamoDbHelper.getInstance().startSpan(sdkRequest, sdkHttpFullRequest.getUri(), executionContext);
            }

            if (span != null) {
                span.activate();
            }

            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
        public static void exitInvoke(@Nullable @Advice.Enter Object spanObj,
                                      @Nullable @Advice.Thrown Throwable thrown) {
            if (spanObj instanceof Span) {
                Span span = (Span) spanObj;
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
    }
}
