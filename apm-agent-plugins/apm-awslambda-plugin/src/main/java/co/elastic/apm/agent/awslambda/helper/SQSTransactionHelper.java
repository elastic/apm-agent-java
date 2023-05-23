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

import co.elastic.apm.agent.awslambda.SQSMessageAttributesGetter;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

import javax.annotation.Nullable;
import java.util.List;

public class SQSTransactionHelper extends AbstractMessageBasedTransactionHelper<SQSEvent, Void, SQSEvent.SQSMessage> {

    @Nullable
    private static SQSTransactionHelper INSTANCE;

    private final SQSEvent.SQSMessage placeholderMessage = new SQSEvent.SQSMessage();

    private SQSTransactionHelper(ElasticApmTracer tracer) {
        super(tracer);
    }

    public static SQSTransactionHelper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SQSTransactionHelper(GlobalTracer.get().require(ElasticApmTracer.class));
        }
        return INSTANCE;
    }

    @Override
    protected String getAWSService() {
        return "sqs";
    }

    @Nullable
    @Override
    protected String getQueueArn(SQSEvent.SQSMessage record) {
        return record.getEventSourceArn();
    }

    @Override
    protected String getRegion(SQSEvent.SQSMessage record) {
        return record.getAwsRegion();
    }

    @Override
    protected String getVersion(SQSEvent.SQSMessage record) {
        return null;
    }

    @Override
    protected SQSEvent.SQSMessage getFirstRecord(SQSEvent event) {
        SQSEvent.SQSMessage record = null;
        if (null != event.getRecords() && !event.getRecords().isEmpty()) {
            record = event.getRecords().get(0);
        }
        return record != null ? record : placeholderMessage;
    }

    @Override
    protected void addSpanLinks(Transaction transaction, SQSEvent event) {
        List<SQSEvent.SQSMessage> records = event.getRecords();
        if (records != null && !records.isEmpty()) {
            for (SQSEvent.SQSMessage record : records) {
                transaction.addLink(SQSMessageAttributesGetter.INSTANCE, record);
            }
        }
    }
}
