package co.elastic.apm.agent.grpc.helper;

import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import io.grpc.Metadata;

public class GrpcHeaderSetter implements TextHeaderSetter<Metadata> {

    @Override
    public void setHeader(String headerName, String headerValue, Metadata carrier) {
        carrier.put(Metadata.Key.of(headerName, Metadata.ASCII_STRING_MARSHALLER), headerValue);
    }

}
