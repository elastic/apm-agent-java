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

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageNotWriteableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JmsMessagePropertyAccessorTest extends AbstractInstrumentationTest {

    @Test
    void returnsNullOnJMSException() throws JMSException {
        Message msg = mock(Message.class);

        doThrow(JMSException.class).when(msg).getStringProperty(any(String.class));

        assertThat(JmsMessagePropertyAccessor.instance().getFirstHeader("header", msg)).isNull();
    }

    @Test
    void setHeader() throws JMSException {
        Message msg = mock(Message.class);

        ArgumentCaptor<String> headerValue = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> headerName = ArgumentCaptor.forClass(String.class);

        doReturn(null).when(msg).getStringProperty(any(String.class));

        JmsMessagePropertyAccessor.instance().setHeader(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, "header-value", msg);

        verify(msg).setStringProperty(headerName.capture(), headerValue.capture());

        assertThat(headerName.getValue()).isEqualTo(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME.replace("-", "_"));
        assertThat(headerValue.getValue()).isEqualTo("header-value");
    }

    @Test
    void setHeaderSkipIfPresent() throws JMSException {
        Message msg = mock(Message.class);

        String header = "header";
        String newValue = "new-value";

        doReturn("msg-value").when(msg).getStringProperty(header);
        doThrow(RuntimeException.class).when(msg).setStringProperty(header, newValue);

        JmsMessagePropertyAccessor.instance().setHeader(header, newValue, msg);
    }

    @ParameterizedTest
    @ValueSource(classes = {JMSException.class, MessageNotWriteableException.class})
    void setHeaderException(Class<? extends JMSException> exceptionType) throws JMSException {
        Message msg = mock(Message.class);
        doThrow(exceptionType).when(msg).setStringProperty(any(String.class), any(String.class));

        JmsMessagePropertyAccessor.instance().setHeader("", "", msg);
    }

}
