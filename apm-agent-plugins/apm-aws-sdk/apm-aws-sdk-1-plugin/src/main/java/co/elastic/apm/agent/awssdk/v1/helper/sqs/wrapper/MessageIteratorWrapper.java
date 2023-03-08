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
package co.elastic.apm.agent.awssdk.v1.helper.sqs.wrapper;

import co.elastic.apm.agent.awssdk.common.AbstractMessageIteratorWrapper;
import co.elastic.apm.agent.awssdk.v1.helper.SQSHelper;
import co.elastic.apm.agent.tracer.Tracer;
import com.amazonaws.services.sqs.model.Message;

import java.util.Iterator;

class MessageIteratorWrapper extends AbstractMessageIteratorWrapper<Message> {
    public MessageIteratorWrapper(Iterator<Message> delegate, Tracer tracer, String queueName) {
        super(delegate, tracer, queueName, SQSHelper.getInstance(), SQSHelper.getInstance());
    }
}
