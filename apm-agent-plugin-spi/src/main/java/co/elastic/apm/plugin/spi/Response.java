package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;
import java.util.Collection;

public interface Response {

    PotentiallyMultiValuedMap getHeaders();

    Response withFinished(boolean finished);

    Response withStatusCode(int statusCode);

    Response withHeadersSent(boolean headersSent);

    Response addHeader(String headerName, @Nullable Collection<String> headerValues);
}
