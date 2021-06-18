package co.elastic.apm.agent.springwebflux;

import co.elastic.apm.agent.impl.transaction.AbstractTextHeaderGetterTest;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Map;

class HeaderGetterTest extends AbstractTextHeaderGetterTest<HeaderGetter, HttpHeaders> {

    @Override
    protected HeaderGetter createTextHeaderGetter() {
        return new HeaderGetter();
    }

    @Override
    protected HttpHeaders createCarrier(Map<String,List<String>> map) {
        HttpHeaders headers = new HttpHeaders();
        map.forEach(headers::put);
        return headers;
    }

}
