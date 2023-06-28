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
import co.elastic.apm.agent.tracer.dispatch.AbstractHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;

import javax.annotation.Nullable;

abstract public class JmsMessagePropertyAccessor<MESSAGE, JMSEXCEPTION extends Exception> extends AbstractHeaderGetter<String, MESSAGE> implements TextHeaderGetter<MESSAGE>, TextHeaderSetter<MESSAGE> {

    private static final Logger logger = LoggerFactory.getLogger(JmsMessagePropertyAccessor.class);

    private final JmsInstrumentationHelper helper;

    protected JmsMessagePropertyAccessor(JmsInstrumentationHelper helper) {
        this.helper = helper;
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, MESSAGE message) {
        headerName = helper.resolvePossibleTraceHeader(headerName);
        String value = null;
        try {
            value = (String) helper.getObjectProperty(message, headerName);
        } catch (Exception e) {
            logger.error("Failed to extract JMS message property {}", headerName, e);
        }
        return value;
    }

    @Override
    public void setHeader(String headerName, String headerValue, MESSAGE message) {
        headerName = helper.resolvePossibleTraceHeader(headerName);
        if (getFirstHeader(headerName, message) != null) {
            return;
        }
        try {
            helper.setObjectProperty(message, headerName, headerValue);
        } catch (javax.jms.MessageNotWriteableException | jakarta.jms.MessageNotWriteableException e) {
            logger.debug("Failed to set JMS message property {} due to read-only message", headerName, e);
        } catch (Exception e) {
            logger.warn("Failed to set JMS message property {}. Distributed tracing may not work.", headerName);
            logger.debug("Detailed error: ", e);
        }
    }
}
