package co.elastic.apm.agent.httpclient.v5.helper;

import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequest;

import javax.annotation.Nullable;

public class RequestHeaderAccessor implements TextHeaderGetter<HttpRequest>, TextHeaderSetter<HttpRequest> {

    public static final RequestHeaderAccessor INSTANCE = new RequestHeaderAccessor();

    @Nullable
    @Override
    public String getFirstHeader(String headerName, HttpRequest request) {
        Header header = request.getFirstHeader(headerName);
        if (header == null) {
            return null;
        }
        return header.getValue();
    }

    @Override
    public <S> void forEach(String headerName, HttpRequest carrier, S state, HeaderConsumer<String, S> consumer) {
        Header[] headers = carrier.getHeaders(headerName);
        if (headers == null) {
            return;
        }
        for (Header header : headers) {
            consumer.accept(header.getValue(), state);
        }
    }

    @Override
    public void setHeader(String headerName, String headerValue, HttpRequest request) {
        request.setHeader(headerName, headerValue);
    }
}
