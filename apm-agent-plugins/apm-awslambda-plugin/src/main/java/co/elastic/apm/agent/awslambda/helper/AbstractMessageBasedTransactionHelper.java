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

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.CloudOrigin;
import co.elastic.apm.agent.impl.context.ServiceOrigin;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.util.PrivilegedActionUtils;
import com.amazonaws.services.lambda.runtime.Context;

import javax.annotation.Nullable;

public abstract class AbstractMessageBasedTransactionHelper<I, O, R> extends AbstractLambdaTransactionHelper<I, O> {
    protected static final String TRANSACTION_TYPE = "messaging";

    protected AbstractMessageBasedTransactionHelper(ElasticApmTracer tracer) {
        super(tracer);
    }

    protected abstract String getAWSService();

    @Nullable
    protected abstract String getQueueArn(R record);

    @Nullable
    protected abstract String getRegion(R record);

    @Nullable
    protected abstract String getVersion(R record);

    protected abstract R getFirstRecord(I event);

    @Nullable
    @Override
    protected Transaction doStartTransaction(I event, Context lambdaContext) {
        Transaction transaction = tracer.startRootTransaction(PrivilegedActionUtils.getClassLoader(lambdaContext.getClass()));
        if (null != transaction) {
            addSpanLinks(transaction, event);
        }
        return transaction;
    }

    protected abstract void addSpanLinks(Transaction transaction, I event);

    @Override
    public void captureOutputForTransaction(Transaction transaction, O output) {
        // Nothing to do here
    }

    @Override
    protected void setTransactionTriggerData(Transaction transaction, I event) {
        R record = getFirstRecord(event);

        transaction.withType(TRANSACTION_TYPE);
        transaction.getFaas().getTrigger().withType("pubsub");
        ServiceOrigin serviceOrigin = transaction.getContext().getServiceOrigin();

        CloudOrigin cloudOrigin = transaction.getContext().getCloudOrigin();
        cloudOrigin.withProvider("aws");
        cloudOrigin.withServiceName(getAWSService());

        String region = getRegion(record);

        String queueArn = getQueueArn(record);
        if (null != queueArn) {
            String queueName = null;
            String accountId = null;
            String[] arnSegments = queueArn.split(":", -1);
            if (arnSegments.length >= 6) {
                queueName = arnSegments[5].isEmpty() ? null : arnSegments[5];
                accountId = arnSegments[4].isEmpty() ? null : arnSegments[4];
                if (region == null && !arnSegments[3].isEmpty()) {
                    region = arnSegments[3];
                }
            }

            updateTransactionName(transaction, queueName);

            serviceOrigin.withId(queueArn);
            serviceOrigin.withName(queueName);
            String serviceOriginVersion = getVersion(record);
            if (null != serviceOriginVersion) {
                serviceOrigin.withVersion(serviceOriginVersion);
            }
            cloudOrigin.withAccountId(accountId);
            if (null != region) {
                cloudOrigin.withRegion(region);
            }
        }
    }

    private void updateTransactionName(Transaction transaction, @Nullable String queueName) {
        StringBuilder transactionName = transaction.getAndOverrideName(AbstractSpan.PRIORITY_HIGH_LEVEL_FRAMEWORK);
        if (null != transactionName && null != queueName && !queueName.isEmpty()) {
            transactionName.append("RECEIVE ").append(queueName);
        }
    }
}
