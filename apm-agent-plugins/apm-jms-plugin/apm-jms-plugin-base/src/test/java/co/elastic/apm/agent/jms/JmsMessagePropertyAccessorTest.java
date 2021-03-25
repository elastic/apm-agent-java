package co.elastic.apm.agent.jms;

import co.elastic.apm.agent.impl.transaction.TraceContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.jms.JMSException;
import javax.jms.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JmsMessagePropertyAccessorTest {

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
}
