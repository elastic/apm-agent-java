package co.elastic.apm.agent.jettyclient.helper;

import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;

import javax.annotation.Nullable;

public class HttpFieldAccessor implements TextHeaderGetter<HttpRequest>, TextHeaderSetter<HttpRequest> {
    public static final HttpFieldAccessor INSTANCE = new HttpFieldAccessor();

    @Override
    public void setHeader(String headerName, String headerValue, HttpRequest httpRequest) {
        httpRequest.header(headerName, headerValue);
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, HttpRequest httpRequest) {
        return httpRequest.getHeaders() != null ? httpRequest.getHeaders().get(headerName) : null;
    }

    @Override
    public <S> void forEach(String headerName, HttpRequest httpRequest, S state, HeaderConsumer<String, S> consumer) {
        HttpFields httpFields = httpRequest.getHeaders();
        if (httpFields != null) {
            for (HttpField httpField : httpFields) {
                consumer.accept(httpField.getValue(), state);
            }
        }
    }
}
