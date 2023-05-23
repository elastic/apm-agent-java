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
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.impl.context.CloudOrigin;
import co.elastic.apm.agent.impl.context.ServiceOrigin;
import co.elastic.apm.agent.impl.transaction.FaasTrigger;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.util.PrivilegedActionUtils;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;

import javax.annotation.Nullable;

public class S3TransactionHelper extends AbstractLambdaTransactionHelper<S3Event, Void> {
    protected static final String TRANSACTION_TYPE = "messaging";

    @Nullable
    private static S3TransactionHelper INSTANCE;

    private S3TransactionHelper(ElasticApmTracer tracer) {
        super(tracer);
    }

    public static S3TransactionHelper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new S3TransactionHelper(GlobalTracer.get().require(ElasticApmTracer.class));
        }
        return INSTANCE;
    }


    @Nullable
    @Override
    protected Transaction doStartTransaction(S3Event s3Event, Context lambdaContext) {
        return tracer.startRootTransaction(PrivilegedActionUtils.getClassLoader(lambdaContext.getClass()));
    }

    @Override
    public void captureOutputForTransaction(Transaction transaction, Void output) {
        // Nothing to do here
    }

    @Override
    protected void setTransactionTriggerData(Transaction transaction, S3Event s3Event) {
        transaction.withType(TRANSACTION_TYPE);

        FaasTrigger faasTrigger = transaction.getFaas().getTrigger();
        faasTrigger.withType("datasource");

        CloudOrigin cloudOrigin = transaction.getContext().getCloudOrigin();
        cloudOrigin.withProvider("aws").withServiceName("s3");

        S3EventNotification.S3EventNotificationRecord s3NotificationRecord = getS3NotificationRecord(s3Event);
        if (null != s3NotificationRecord) {
            String requestId = null != s3NotificationRecord.getResponseElements() ? s3NotificationRecord.getResponseElements().getxAmzRequestId() : null;
            faasTrigger.withRequestId(requestId);

            cloudOrigin.withRegion(s3NotificationRecord.getAwsRegion());

            if (null != s3NotificationRecord.getS3() && null != s3NotificationRecord.getS3().getBucket()) {
                S3EventNotification.S3BucketEntity bucket = s3NotificationRecord.getS3().getBucket();
                ServiceOrigin serviceOrigin = transaction.getContext().getServiceOrigin();
                serviceOrigin.withId(bucket.getArn());
                serviceOrigin.withName(bucket.getName());
                serviceOrigin.withVersion(s3NotificationRecord.getEventVersion());
            }
        }
    }

    @Override
    protected void setTransactionName(Transaction transaction, S3Event s3Event, Context lambdaContext) {
        S3EventNotification.S3EventNotificationRecord s3NotificationRecord = getS3NotificationRecord(s3Event);
        StringBuilder transactionName = transaction.getAndOverrideName(AbstractSpan.PRIORITY_HIGH_LEVEL_FRAMEWORK);
        if (transactionName != null && null != s3NotificationRecord && null != s3NotificationRecord.getS3() && null != s3NotificationRecord.getS3().getBucket()) {
            transactionName.append(s3NotificationRecord.getEventName()).append(" ").append(s3NotificationRecord.getS3().getBucket().getName());
        } else {
            super.setTransactionName(transaction, s3Event, lambdaContext);
        }
    }

    @Nullable
    private S3EventNotification.S3EventNotificationRecord getS3NotificationRecord(S3Event s3Event) {
        if (null != s3Event.getRecords() && s3Event.getRecords().size() == 1) {
            return s3Event.getRecords().get(0);
        }
        return null;
    }
}
