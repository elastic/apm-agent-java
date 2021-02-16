package co.elastic.apm.agent.rabbitmq.header;

import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;

import javax.annotation.Nullable;
import java.util.Map;

public abstract class AbstractTextHeaderGetter<T> implements TextHeaderGetter<T> {

    @Nullable
    @Override
    public String getFirstHeader(String headerName, T carrier) {
        Map<String, Object> headers = getHeaders(carrier);
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        Object headerValue = headers.get(headerName);
        if (headerValue != null) {
            // com.rabbitmq.client.impl.LongStringHelper.ByteArrayLongString
            return headerValue.toString();
        }
        return null;
    }

    @Override
    public <S> void forEach(String headerName, T carrier, S state, HeaderConsumer<String, S> consumer) {
        String header = getFirstHeader(headerName, carrier);
        if (header != null) {
            consumer.accept(header, state);
        }
    }

    protected abstract Map<String, Object> getHeaders(T carrier);

}
