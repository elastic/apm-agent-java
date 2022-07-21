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
package co.elastic.apm.agent.awslambda.lambdas;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

public class TestContext implements Context {
    public static final String AWS_REQUEST_ID = "AWS_REQUEST_ID";
    public static final String LOG_GROUP_NAME = "LOG_GROUP_NAME";
    public static final String LOG_STREAM_NAME = "LOG_STREAM_NAME";
    public static final String FUNCTION_NAME = "FUNCTION_NAME";
    public static final String FUNCTION_EXECUTION_ENV = "AWS_Lambda_java11";
    public static final String FUNCTION_VERSION = "FUNCTION_VERSION";
    public static final String FUNCTION_REGION = "us-west-2";
    public static final String FUNCTION_ACCOUNT_ID = "123456789012";
    public static final String FUNCTION_ARN = "arn:aws:lambda:"+FUNCTION_REGION+":"+FUNCTION_ACCOUNT_ID+":function:" + FUNCTION_NAME;
    public static final String FUNCTION_ARN_WITH_LABEL = FUNCTION_ARN + ":someLabel";

    private boolean raiseException;
    private boolean setErrorStatusCode;

    @Override
    public String getAwsRequestId() {
        return AWS_REQUEST_ID;
    }

    @Override
    public String getLogGroupName() {
        return LOG_GROUP_NAME;
    }

    @Override
    public String getLogStreamName() {
        return LOG_STREAM_NAME;
    }

    @Override
    public String getFunctionName() {
        return FUNCTION_NAME;
    }

    @Override
    public String getFunctionVersion() {
        return FUNCTION_VERSION;
    }

    @Override
    public String getInvokedFunctionArn() {
        return FUNCTION_ARN_WITH_LABEL;
    }

    @Override
    public CognitoIdentity getIdentity() {
        return null;
    }

    @Override
    public ClientContext getClientContext() {
        return null;
    }

    @Override
    public int getRemainingTimeInMillis() {
        return 0;
    }

    @Override
    public int getMemoryLimitInMB() {
        return 0;
    }

    @Override
    public LambdaLogger getLogger() {
        return null;
    }

    public boolean shouldRaiseException() {
        return raiseException;
    }

    public void raiseException() {
        raiseException = true;
    }

    public boolean shouldSetErrorStatusCode() {
        return setErrorStatusCode;
    }

    public void setErrorStatusCode() {
        setErrorStatusCode = true;
    }
}
