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
package co.elastic.apm.agent.jms;

import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.TraceHeaderNameEncoding;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.dispatch.AbstractHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageNotWriteableException;

public class JmsMessagePropertyAccessor extends AbstractHeaderGetter<String, Message> implements TextHeaderGetter<Message>, TextHeaderSetter<Message> {

    private static final Logger logger = LoggerFactory.getLogger(JmsMessagePropertyAccessor.class);

    private static final JmsMessagePropertyAccessor INSTANCE = new JmsMessagePropertyAccessor();

    public static JmsMessagePropertyAccessor instance() {
        return INSTANCE;
    }

    private final Tracer tracer;

    private JmsMessagePropertyAccessor() {
        tracer = GlobalTracer.get();
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, Message message) {
        headerName = jmsifyHeaderName(headerName);
        String value = null;
        try {
            value = message.getStringProperty(headerName);
        } catch (JMSException e) {
            logger.error("Failed to extract JMS message property {}", headerName, e);
        }
        return value;
    }

    @Nonnull
    private String jmsifyHeaderName(String headerName) {
        if (tracer.getTraceHeaderNames(TraceHeaderNameEncoding.REGULAR).contains(headerName)) {
            // replacing with the JMS equivalent
            headerName = TraceHeaderNameEncoding.QUEUE.encode(headerName);
        }
        return headerName;
    }

    @Override
    public void setHeader(String headerName, String headerValue, Message message) {
        headerName = jmsifyHeaderName(headerName);
        if (getFirstHeader(headerName, message) != null) {
            return;
        }
        try {
            message.setStringProperty(headerName, headerValue);
        } catch (MessageNotWriteableException e) {
            logger.debug("Failed to set JMS message property {} due to read-only message", headerName, e);
        } catch (JMSException e) {
            logger.warn("Failed to set JMS message property {}. Distributed tracing may not work.", headerName);
            logger.debug("Detailed error: ", e);
        }
    }
}
