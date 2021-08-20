package co.elastic.apm.agent.jettyclient.helper;

import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import org.eclipse.jetty.client.HttpRequest;

public class HttpFieldAccessor implements TextHeaderSetter<HttpRequest> {
    public static final HttpFieldAccessor INSTANCE = new HttpFieldAccessor();

    @Override
    public void setHeader(String headerName, String headerValue, HttpRequest httpRequest) {
        httpRequest.header(headerName, headerValue);
    }
}
