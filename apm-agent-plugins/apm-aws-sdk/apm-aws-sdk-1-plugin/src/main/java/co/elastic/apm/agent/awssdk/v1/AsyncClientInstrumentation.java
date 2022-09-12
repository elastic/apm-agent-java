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
import co.elastic.apm.agent.awssdk.v1.helper.Constants;
import com.amazonaws.AmazonWebServiceRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * If the async client is used to trigger the AWS requests, we set a handler context value to indicate that it's an async span.
 */
public class AsyncClientInstrumentation extends AbstractAwsSdkInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("com.amazonaws.services.sqs.AmazonSQSAsyncClient")
            .or(named("com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return nameEndsWith("Async")
            .and(takesArguments(1))
            .and(takesArgument(0, nameEndsWith("Request")));
    }

    public static class AdviceClass {
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void enterMethodEnter(@Advice.Argument(value = 0) AmazonWebServiceRequest request) {
            request.addHandlerContext(Constants.ASYNC_HANDLER_CONTEXT, true);
        }
    }
}
