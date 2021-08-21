package co.elastic.apm.agent.jettyclient.helper;

import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import org.eclipse.jetty.client.api.Request;

public class HttpFieldAccessor implements TextHeaderSetter<Request> {
    public static final HttpFieldAccessor INSTANCE = new HttpFieldAccessor();

    @Override
    public void setHeader(String headerName, String headerValue, Request httpRequest) {
        httpRequest.header(headerName, headerValue);
    }
}
