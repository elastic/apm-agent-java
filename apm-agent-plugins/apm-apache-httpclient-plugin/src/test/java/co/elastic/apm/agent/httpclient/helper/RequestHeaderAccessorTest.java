package co.elastic.apm.agent.httpclient.helper;

import co.elastic.apm.agent.impl.transaction.AbstractTextHeaderGetterTest;
import org.apache.http.HttpRequest;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;

class RequestHeaderAccessorTest {

    @Nested
    class GetterTest extends AbstractTextHeaderGetterTest<RequestHeaderAccessor, HttpRequest> {

        @Override
        protected RequestHeaderAccessor createTextHeaderGetter() {
            return RequestHeaderAccessor.INSTANCE;
        }

        @Override
        protected HttpRequest createCarrier(Map<String, List<String>> map) {
            HttpRequest request = new BasicHttpRequest("GET", "http://fake/");
            map.forEach((k, values) -> values.forEach(v -> request.addHeader(new BasicHeader(k, v))));
            return request;
        }
    }

}
