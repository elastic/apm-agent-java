package co.elastic.apm.agent.httpclient.v5.helper;

import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;

import javax.annotation.Nullable;

public class RequestHeaderAccessor implements TextHeaderGetter<ClassicHttpRequest>, TextHeaderSetter<ClassicHttpRequest> {

    public static final RequestHeaderAccessor INSTANCE = new RequestHeaderAccessor();

    @Nullable
    @Override
    public String getFirstHeader(String headerName, ClassicHttpRequest request) {
        Header header = request.getFirstHeader(headerName);
        if (header == null) {
            return null;
        }
        return header.getValue();
    }

    @Override
    public <S> void forEach(String headerName, ClassicHttpRequest carrier, S state, HeaderConsumer<String, S> consumer) {
        Header[] headers = carrier.getHeaders(headerName);
        if (headers == null) {
            return;
        }
        for (Header header : headers) {
            consumer.accept(header.getValue(), state);
        }
    }

    @Override
    public void setHeader(String headerName, String headerValue, ClassicHttpRequest request) {
        request.setHeader(headerName, headerValue);
    }
}
