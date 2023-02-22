package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;
import java.util.Collection;

public class EmptyResponse implements Response {

    public static final Response INSTANCE = new EmptyResponse();

    private EmptyResponse() {
    }

    @Override
    public PotentiallyMultiValuedMap getHeaders() {
        return EmptyPotentiallyMultiValuedMap.INSTANCE;
    }

    @Override
    public Response withFinished(boolean finished) {
        return this;
    }

    @Override
    public Response withStatusCode(int statusCode) {
        return this;
    }

    @Override
    public Response withHeadersSent(boolean headersSent) {
        return this;
    }

    @Override
    public Response addHeader(String headerName, @Nullable Collection<String> headerValues) {
        return this;
    }
}
