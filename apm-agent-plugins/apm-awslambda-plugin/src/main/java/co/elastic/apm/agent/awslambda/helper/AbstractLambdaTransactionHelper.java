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
package co.elastic.apm.agent.awslambda.helper;

import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.tracer.configuration.CoreConfiguration;
import co.elastic.apm.agent.tracer.configuration.ServerlessConfiguration;
import co.elastic.apm.agent.tracer.configuration.WebConfiguration;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.sdk.internal.util.VersionUtils;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import javax.annotation.Nullable;

public abstract class AbstractLambdaTransactionHelper<I, O> {
    private static final Logger logger = LoggerFactory.getLogger(AbstractLambdaTransactionHelper.class);

    protected final Tracer tracer;

    protected final ServerlessConfiguration serverlessConfiguration;
    protected final CoreConfiguration coreConfiguration;
    protected final WebConfiguration webConfiguration;

    protected AbstractLambdaTransactionHelper(Tracer tracer) {
        this.tracer = tracer;
        this.coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        this.webConfiguration = tracer.getConfig(WebConfiguration.class);
        this.serverlessConfiguration = tracer.getConfig(ServerlessConfiguration.class);
    }

    protected abstract void setTransactionTriggerData(Transaction<?> transaction, I input);

    @Nullable
    protected abstract Transaction<?> doStartTransaction(I input, Context lambdaContext);

    protected abstract void captureOutputForTransaction(Transaction<?> transaction, O output);

    private static boolean coldStart = true;

    @Nullable
    private String functionArn;

    @Nullable
    public Transaction<?> startTransaction(I input, Context lambdaContext) {
        boolean isColdStart = coldStart;
        if (isColdStart) {
            completeMetaData(lambdaContext);
            coldStart = false;
        }
        Transaction<?> transaction = doStartTransaction(input, lambdaContext);
        if (null != transaction) {
            transaction.getFaas()
                .withId(getFaasId(lambdaContext))
                .withName(lambdaContext.getFunctionName())
                .withVersion(lambdaContext.getFunctionVersion())
                .withColdStart(isColdStart)
                .withExecution(lambdaContext.getAwsRequestId());
            transaction.getContext().getCloudOrigin().withProvider("aws");
            setTransactionName(transaction, input, lambdaContext);
            setTransactionTriggerData(transaction, input);
            return transaction.activate();
        }

        return null;
    }

    private String getFaasId(Context lambdaContext) {
        if (functionArn == null) {
            functionArn = lambdaContext.getInvokedFunctionArn();
            String[] arnSegments = functionArn.split(":");
            if (arnSegments.length > 7) {
                functionArn = functionArn.substring(0, functionArn.lastIndexOf(':'));
            }
        }

        return functionArn;
    }

    public void finalizeTransaction(Transaction<?> transaction, @Nullable O output, @Nullable Throwable thrown) {
        try {
            if (null != output) {
                captureOutputForTransaction(transaction, output);
            }
            if (thrown != null) {
                transaction.captureException(thrown);
                transaction.withResultIfUnset("failure");
            } else {
                transaction.withResultIfUnset("success");
            }
        } finally {
            transaction.deactivate().end();
        }
        tracer.flush();
    }

    private void completeMetaData(Context lambdaContext) {
        try {
            String[] arnSegments = lambdaContext.getInvokedFunctionArn().split(":");
            String region = arnSegments[3];
            String accountId = arnSegments[4];

            // set framework
            String lambdaLibVersion = VersionUtils.getVersion(RequestHandler.class, "com.amazonaws", "aws-lambda-java-core");
            if (lambdaLibVersion == null) {
                lambdaLibVersion = "unknown";
            }

            tracer.completeMetaData("AWS Lambda", lambdaLibVersion, accountId, region);
        } catch (Exception e) {
            logger.error("Failed updating metadata for first lambda execution!", e);
        }
    }

    protected void setTransactionName(Transaction<?> transaction, I event, Context lambdaContext) {
        StringBuilder transactionName = transaction.getAndOverrideName(AbstractSpan.PRIORITY_HIGH_LEVEL_FRAMEWORK);
        if (transactionName != null) {
            transactionName.append(lambdaContext.getFunctionName());
        }
    }
}
