package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class HttpClientRequestPropertyAccessor implements TextHeaderSetter<Map<String, List<String>>> {

    private static final HttpClientRequestPropertyAccessor INSTANCE = new HttpClientRequestPropertyAccessor();

    public static HttpClientRequestPropertyAccessor instance() {
        return INSTANCE;
    }

    @Override
    public void setHeader(String headerName, String headerValue, Map<String, List<String>> headersMap) {
        headersMap.put(headerName, Collections.singletonList(headerValue));
    }
}
