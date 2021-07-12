package co.elastic.apm.agent.servlet.helper;

import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;

import javax.annotation.Nullable;
import java.util.Enumeration;

public abstract class CommonServletRequestHeaderGetter<T> implements TextHeaderGetter<T> {
    @Nullable
    @Override
    public String getFirstHeader(String headerName, T carrier) {
        return getHeader(headerName, carrier);
    }

    abstract String getHeader(String headerName, T carrier);

    @Override
    public <S> void forEach(String headerName, T carrier, S state, HeaderConsumer<String, S> consumer) {
        Enumeration<String> headers = getHeaders(headerName, carrier);
        while (headers.hasMoreElements()) {
            consumer.accept(headers.nextElement(), state);
        }
    }

    abstract Enumeration<String> getHeaders(String headerName, T carrier);

}
