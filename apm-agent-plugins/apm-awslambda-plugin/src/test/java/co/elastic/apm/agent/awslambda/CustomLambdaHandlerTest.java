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

import co.elastic.apm.agent.awslambda.lambdas.AbstractFunction;
import co.elastic.apm.agent.awslambda.lambdas.CustomHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

public class CustomLambdaHandlerTest extends AbstractPlainLambdaTest {

    private final CustomHandler customHandler = new CustomHandler();

    @BeforeAll
    // Need to overwrite the beforeAll() method from parent,
    // because we need to mock serverlessConfiguration BEFORE instrumentation is initialized!
    public static synchronized void beforeAll() {
        AbstractLambdaTest.initAllButInstrumentation();
        when(Objects.requireNonNull(serverlessConfiguration).getAwsLambdaHandler()).thenReturn(
            "co.elastic.apm.agent.awslambda.lambdas.CustomHandler::customHandleRequest"
        );
        AbstractLambdaTest.initInstrumentation();
    }

    @Test
    public void testMetaData() throws Exception {
        customHandler.customHandleRequest(null, context);
        verifyMetaData();
    }

    @Test
    public void testBasicCall() {
        customHandler.customHandleRequest(null, context);
        verifyTransactionDetails();
    }

    /**
     * Overriding the base test that relies on {@link com.amazonaws.services.lambda.runtime.RequestHandler} implementation
     */
    @Test
    public void testCallWithHandlerError() {
        Objects.requireNonNull(context).raiseException();
        assertThatThrownBy(() -> customHandler.customHandleRequest(createInput(), context)).isInstanceOf(RuntimeException.class);
        verifyFailure();
    }

    /**
     * The handler returned by this method should not be used for the custom handler tests
     * @return {@code null}
     */
    @Override
    protected AbstractFunction<Object, Void> createHandler() {
        //noinspection ConstantConditions
        return null;
    }
}
