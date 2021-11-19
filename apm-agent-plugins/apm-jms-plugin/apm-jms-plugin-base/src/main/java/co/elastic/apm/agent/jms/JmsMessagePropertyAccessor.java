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

import co.elastic.apm.agent.impl.transaction.AbstractHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static co.elastic.apm.agent.jms.JavaxJmsInstrumentationHelper.JMS_TRACE_PARENT_PROPERTY;

abstract public class JmsMessagePropertyAccessor<MESSAGE, JMSEXCEPTION extends Exception> extends AbstractHeaderGetter<String, MESSAGE> implements TextHeaderGetter<MESSAGE>, TextHeaderSetter<MESSAGE> {

    private static final Logger logger = LoggerFactory.getLogger(JmsMessagePropertyAccessor.class);

    protected JmsMessagePropertyAccessor() {
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, MESSAGE message) {
        headerName = jmsifyHeaderName(headerName);
        String value = null;
        try {
            value = getStringProperty(message, headerName);
        } catch (Exception e) {
            logger.error("Failed to extract JMS message property {}", headerName, e);
        }
        return value;
    }

    @Nonnull
    private String jmsifyHeaderName(String headerName) {
        if (headerName.equals(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME)) {
            // replacing with the JMS equivalent
            headerName = JMS_TRACE_PARENT_PROPERTY;
        }
        return headerName;
    }

    @Override
    public void setHeader(String headerName, String headerValue, MESSAGE message) {
        headerName = jmsifyHeaderName(headerName);
        if (getFirstHeader(headerName, message) != null) {
            return;
        }
        try {
            setStringProperty(message, headerName, headerValue);
        } catch (Exception e) {
            if (isMessageNotWriteableException(e)) {
                logger.debug("Failed to set JMS message property {} due to read-only message", headerName, e);
            }
            logger.warn("Failed to set JMS message property {}. Distributed tracing may not work.", headerName);
            logger.debug("Detailed error: ", e);
        }
    }

    abstract String getStringProperty(MESSAGE message, String name) throws JMSEXCEPTION;

    abstract void setStringProperty(MESSAGE message, String name, String value) throws JMSEXCEPTION;

    abstract boolean isMessageNotWriteableException(Exception e);
}
