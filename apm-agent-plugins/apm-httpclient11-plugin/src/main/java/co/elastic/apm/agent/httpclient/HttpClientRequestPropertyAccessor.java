package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;

import java.net.http.HttpRequest;

@SuppressWarnings("unused")
public class HttpClientRequestPropertyAccessor implements TextHeaderSetter<HttpRequest.Builder> {

    private static final HttpClientRequestPropertyAccessor INSTANCE = new HttpClientRequestPropertyAccessor();

    public static HttpClientRequestPropertyAccessor instance() {
        return INSTANCE;
    }

    @Override
    public void setHeader(String headerName, String headerValue, HttpRequest.Builder httpRequestBuilder) {
        httpRequestBuilder.setHeader(headerName, headerValue);
    }
}
