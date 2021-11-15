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
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

public class SQSTransactionHelper extends AbstractMessageBasedTransactionHelper<SQSEvent, Void, SQSEvent.SQSMessage> {

    private static final String AWS_MESSAGE_SENT_TIMESTAMP_KEY = "SentTimestamp";

    @Nullable
    private static SQSTransactionHelper INSTANCE;

    private final SQSEvent.SQSMessage placeholderMessage = new SQSEvent.SQSMessage();

    private SQSTransactionHelper(ElasticApmTracer tracer) {
        super(tracer);
    }

    public static SQSTransactionHelper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SQSTransactionHelper(GlobalTracer.requireTracerImpl());
        }
        return INSTANCE;
    }

    @Override
    protected TextHeaderGetter<SQSEvent.SQSMessage> getTextHeaderGetter() {
        return SQSMessageAttributesGetter.INSTANCE;
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
    protected long getMessageTimestampMs(SQSEvent.SQSMessage record) {
        if (null != record.getMessageAttributes() && record.getMessageAttributes().containsKey(AWS_MESSAGE_SENT_TIMESTAMP_KEY)) {
            try {
                String strValue = getHeaderValue(record, AWS_MESSAGE_SENT_TIMESTAMP_KEY);
                return strValue != null ? Long.parseLong(strValue) : -1L;
            } catch (Exception e) {
                return -1L;
            }
        }
        return -1L;
    }

    @Override
    protected String getBody(SQSEvent.SQSMessage record) {
        return record.getBody();
    }

    @Override
    protected String getMessageId(SQSEvent.SQSMessage record) {
        return record.getMessageId();
    }

    @Override
    protected String getRegion(SQSEvent.SQSMessage record) {
        return record.getAwsRegion();
    }

    @Override
    protected Collection<String> getHeaderNames(SQSEvent.SQSMessage record) {
        if (null != record.getMessageAttributes()) {
            return record.getMessageAttributes().keySet();
        }
        return Collections.emptySet();
    }

    @Override
    protected String getHeaderValue(SQSEvent.SQSMessage record, String key) {
        if (null != record.getMessageAttributes() && record.getMessageAttributes().containsKey(key)) {
            return record.getMessageAttributes().get(key).getStringValue();
        }
        return null;
    }

    @Override
    protected String getVersion(SQSEvent.SQSMessage record) {
        return null;
    }

    @Override
    protected SQSEvent.SQSMessage getRecord(SQSEvent event) {
        SQSEvent.SQSMessage record = null;
        if (null != event.getRecords() && event.getRecords().size() == 1) {
            record = event.getRecords().get(0);
        }

        return record != null ? record : placeholderMessage;
    }
}
