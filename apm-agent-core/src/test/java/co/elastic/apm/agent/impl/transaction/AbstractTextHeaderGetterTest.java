/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
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
