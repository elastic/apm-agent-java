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
import co.elastic.apm.agent.awssdk.common.AbstractAwsSdkInstrumentationHelper;
import co.elastic.apm.agent.awssdk.v1.helper.DynamoDbHelper;
import co.elastic.apm.agent.awssdk.v1.helper.S3Helper;
import co.elastic.apm.agent.awssdk.v1.helper.SQSHelper;
import co.elastic.apm.agent.awssdk.v1.helper.SdkV1DataSource;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import com.amazonaws.Request;
import com.amazonaws.handlers.HandlerContextKey;
import com.amazonaws.http.ExecutionContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class AmazonHttpClientInstrumentation extends AbstractAwsSdkInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("com.amazonaws.http.AmazonHttpClient");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute")
            .and(takesArguments(5))
            .and(takesArgument(0, named("com.amazonaws.Request")))
            .and(takesArgument(3, named("com.amazonaws.http.ExecutionContext")));
    }

    public static class AdviceClass {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @Nullable
        public static Object enterInvoke(@Advice.Argument(value = 0) Request<?> request,
                                         @Advice.Argument(value = 3) ExecutionContext executionContext) {
            String awsService = request.getHandlerContext(HandlerContextKey.SERVICE_ID);

            AbstractAwsSdkInstrumentationHelper<Request<?>, ExecutionContext> helper = null;
            if ("S3".equalsIgnoreCase(awsService)) {
                helper = S3Helper.getInstance();
            } else if ("DynamoDB".equalsIgnoreCase(awsService)) {
                helper = DynamoDbHelper.getInstance();
            } else if ("Sqs".equalsIgnoreCase(awsService)) {
                helper = SQSHelper.getInstance();
            }

            if (helper == null) {
                return null;
            }

            Span<?> span = helper.startSpan(request, request.getEndpoint(), executionContext);

            if (span != null) {
                span.activate();
            }

            SdkV1DataSource.getInstance().removeLookupValue(request.getOriginalRequest());

            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
        public static void exitInvoke(@Nullable @Advice.Enter Object spanObj,
                                      @Nullable @Advice.Thrown Throwable thrown) {
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


    }
}
