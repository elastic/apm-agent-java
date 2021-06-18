package co.elastic.apm.agent.impl.transaction;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractTextHeaderGetterTest<G extends TextHeaderGetter<C>, C> {

    protected abstract G createTextHeaderGetter();

    protected abstract C createCarrier(Map<String, List<String>> map);

    @Test
    void missingHeader() {
        G headerGetter = createTextHeaderGetter();
        C headers = createCarrier(Collections.emptyMap());

        assertThat(headerGetter.getFirstHeader("missing", headers)).isNull();

        headerGetter.forEach("missing", headers, "", (headerValue, state) -> {
            throw new IllegalStateException("should not be called");
        });
    }


    @Test
    void singleValueHeader() {
        testHeaderValues(Map.of("key", List.of("value1")));
    }

    @Test
    public void multipleValueHeader() {
        testHeaderValues(Map.of("key", List.of("value1", "value2")));
    }

    private void testHeaderValues(Map<String, List<String>> map) {
        G headerGetter = createTextHeaderGetter();
        C carrier = createCarrier(map);

        map.forEach((k, values) -> {
            assertThat(headerGetter.getFirstHeader(k, carrier)).isEqualTo(values.get(0));

            Object stateObject = createTextHeaderGetter();
            List<String> valuesToConsume = new ArrayList<>(values);
            headerGetter.forEach(k, carrier, stateObject, (headerValue, state) -> {
                assertThat(state).isSameAs(stateObject);
                assertThat(headerValue).isIn(valuesToConsume);
                valuesToConsume.remove(headerValue);
            });

            assertThat(valuesToConsume).isEmpty();
        });


    }


}
