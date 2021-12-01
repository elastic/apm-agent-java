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

import co.elastic.apm.agent.impl.transaction.AbstractHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

import javax.annotation.Nullable;

public class SQSMessageAttributesGetter extends AbstractHeaderGetter<String, SQSEvent.SQSMessage> implements
        TextHeaderGetter<SQSEvent.SQSMessage> {

    public static final SQSMessageAttributesGetter INSTANCE = new SQSMessageAttributesGetter();

    private SQSMessageAttributesGetter() {
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, SQSEvent.SQSMessage carrier) {
        if(null != carrier.getMessageAttributes() && carrier.getMessageAttributes().containsKey(headerName)){
            return carrier.getMessageAttributes().get(headerName).getStringValue();
        }
        return null;
    }
}
