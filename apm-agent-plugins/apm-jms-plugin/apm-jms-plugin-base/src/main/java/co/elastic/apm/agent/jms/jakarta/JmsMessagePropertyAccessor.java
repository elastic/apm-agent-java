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
package co.elastic.apm.agent.jms.jakarta;

import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import jakarta.jms.Message;
import jakarta.jms.MessageNotWriteableException;

public class JmsMessagePropertyAccessor extends co.elastic.apm.agent.jms.JmsMessagePropertyAccessor<Message> {

    private static final Logger logger = LoggerFactory.getLogger(JmsMessagePropertyAccessor.class);

    private static final JmsMessagePropertyAccessor INSTANCE = new JmsMessagePropertyAccessor();

    public static JmsMessagePropertyAccessor instance() {
        return INSTANCE;
    }

    private JmsMessagePropertyAccessor() {
        super(JmsInstrumentationHelper.get());
    }

    @Override
    protected void trySetProperty(String headerName, String headerValue, Message message) throws Exception {
        try {
            helper.setObjectProperty(message, headerName, headerValue);
        } catch (MessageNotWriteableException e) {
            logger.debug("Failed to set JMS message property {} due to read-only message", headerName, e);
        }
    }

}
