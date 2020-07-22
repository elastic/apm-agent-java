package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;

import javax.annotation.Nullable;
import java.net.http.HttpRequest;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("unused")
public class HttpClientRequestPropertyAccessor implements TextHeaderSetter<HttpRequest>, TextHeaderGetter<HttpRequest> {

    private static final HttpClientRequestPropertyAccessor INSTANCE = new HttpClientRequestPropertyAccessor();

    public static HttpClientRequestPropertyAccessor instance() {
        return INSTANCE;
    }

    @Override
    public void setHeader(String headerName, String headerValue, HttpRequest httpRequest) {
        // thrown java.lang.UnsupportedOperationException
        // because headers is immutable list
        httpRequest.headers().map().put(headerName, Collections.singletonList(headerValue));
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, HttpRequest httpRequest) {
        return httpRequest.headers().firstValue(headerName).orElse(null);
    }

    @Override
    public <S> void forEach(String headerName, HttpRequest httpRequest, S state, HeaderConsumer<String, S> consumer) {
        List<String> values = httpRequest.headers().allValues(headerName);
        if (values != null) {
            for (int i = 0, size = values.size(); i < size; i++) {
                consumer.accept(values.get(i), state);
            }
        }
    }
}
