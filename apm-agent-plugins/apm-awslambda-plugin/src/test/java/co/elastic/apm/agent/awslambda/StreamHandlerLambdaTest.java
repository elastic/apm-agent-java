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
package co.elastic.apm.agent.awslambda;

import co.elastic.apm.agent.awslambda.lambdas.StreamHandlerLambdaFunction;
import com.amazonaws.services.lambda.runtime.Context;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeAll;

import java.util.Objects;

import static co.elastic.apm.agent.awslambda.AbstractLambdaTest.serverlessConfiguration;
import static org.mockito.Mockito.when;

public class StreamHandlerLambdaTest extends AbstractStreamHandlerLambdaTest {

    private final StreamHandlerLambdaFunction function = new StreamHandlerLambdaFunction();

    @BeforeAll
    // Need to overwrite the beforeAll() method from parent,
    // because we need to mock serverlessConfiguration BEFORE instrumentation is initialized!
    public static synchronized void beforeAll() {
        AbstractLambdaTest.initAllButInstrumentation();
        when(Objects.requireNonNull(serverlessConfiguration).getAwsLambdaHandler()).thenReturn(StreamHandlerLambdaFunction.class.getName());
        AbstractLambdaTest.initInstrumentation();
    }

    @Override
    protected ThrowableAssert.ThrowingCallable getRequestHandlingInvocation(Context context) {
        return () -> function.handleRequest(null, null, context);
    }
}
