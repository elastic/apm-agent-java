package co.elastic.apm.agent.springwebclient;

import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import org.springframework.web.reactive.function.client.ClientRequest;

import javax.annotation.Nullable;
import java.util.List;

public class ClientRequestHeaderGetter implements TextHeaderGetter<ClientRequest> {

    public static final ClientRequestHeaderGetter INSTANCE = new ClientRequestHeaderGetter();

    @Nullable
    @Override
    public String getFirstHeader(String headerName, ClientRequest carrier) {
        return carrier.headers().getFirst(headerName);
    }

    @Override
    public <S> void forEach(String headerName, ClientRequest carrier, S state, HeaderConsumer<String, S> consumer) {
        List<String> headerValues = carrier.headers().get(headerName);
        if (headerValues == null) {
            return;
        }
        for (String value : headerValues) {
            consumer.accept(value, state);
        }
    }
}
