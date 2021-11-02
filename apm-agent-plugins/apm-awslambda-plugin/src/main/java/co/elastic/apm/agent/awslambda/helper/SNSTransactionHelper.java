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

import co.elastic.apm.agent.awslambda.SNSMessageAttributesGetter;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class SNSTransactionHelper extends AbstractMessageBasedTransactionHelper<SNSEvent, Void, SNSEvent.SNSRecord> {
    @Nullable
    private static SNSTransactionHelper INSTANCE;

    private final SNSEvent.SNSRecord placeholderRecord = new SNSEvent.SNSRecord();

    private SNSTransactionHelper(ElasticApmTracer tracer) {
        super(tracer);
    }

    public static SNSTransactionHelper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SNSTransactionHelper(GlobalTracer.requireTracerImpl());
        }
        return INSTANCE;
    }

    @Override
    protected TextHeaderGetter<SNSEvent.SNSRecord> getTextHeaderGetter() {
        return SNSMessageAttributesGetter.INSTANCE;
    }

    @Override
    protected String getAWSService() {
        return "sns";
    }

    @Override
    protected String getQueueArn(SNSEvent.SNSRecord record) {
        if (null != record.getSNS()) {
            return record.getSNS().getTopicArn();
        }
        return null;
    }

    @Override
    protected long getMessageTimestampMs(SNSEvent.SNSRecord record) {
        if (null != record.getSNS() && null != record.getSNS().getTimestamp()) {
            return record.getSNS().getTimestamp().getMillis();
        }
        return -1L;
    }

    @Override
    protected String getBody(SNSEvent.SNSRecord record) {
        if (null != record.getSNS()) {
            return record.getSNS().getMessage();
        }
        return null;
    }

    @Override
    protected String getMessageId(SNSEvent.SNSRecord record) {
        if (null != record.getSNS()) {
            return record.getSNS().getMessageId();
        }
        return null;
    }

    @Override
    protected String getRegion(SNSEvent.SNSRecord record) {
        // Region will be parsed from ARN in AbstractMessageBasedTransactionHelper
        return null;
    }

    @Override
    protected Collection<String> getHeaderNames(SNSEvent.SNSRecord record) {
        if (null != record.getSNS() && null != record.getSNS().getMessageAttributes()) {
            return record.getSNS().getMessageAttributes().keySet();
        }
        return Collections.emptySet();
    }

    @Override
    protected String getHeaderValue(SNSEvent.SNSRecord record, String key) {
        if (null != record.getSNS() && null != record.getSNS().getMessageAttributes() && record.getSNS().getMessageAttributes().containsKey(key)) {
            return record.getSNS().getMessageAttributes().get(key).getValue();
        }
        return null;
    }

    @Override
    protected String getVersion(SNSEvent.SNSRecord record) {
        return record.getEventVersion();
    }

    @Override
    protected SNSEvent.SNSRecord getRecord(SNSEvent event) {
        SNSEvent.SNSRecord record = null;
        if (null != event.getRecords() && event.getRecords().size() == 1) {
            record = event.getRecords().get(0);
        }

        return record != null ? record : placeholderRecord;
    }

}
