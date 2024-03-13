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
package co.elastic.apm.agent.impl.baggage;

import co.elastic.apm.agent.impl.TextHeaderMapAccessor;
import co.elastic.apm.agent.impl.Utf8HeaderMapAccessor;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.UTF8ByteHeaderGetter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;


public class W3CBaggagePropagationTest {

    @Nested
    public class Propagation {

        @Test
        public void testComplexValue() {
            BaggageImpl baggage = BaggageImpl.builder()
                .put("foo", "bar")
                .put("my_key!", "hello, world!?%ä=", "metadata=blub")
                .build();

            Map<String, String> resultHeaders = doPropagate(baggage);

            assertThat(resultHeaders)
                .hasSize(1)
                .containsEntry("baggage", "foo=bar,my_key!=hello%2C%20world!%3F%25%C3%A4%3D;metadata=blub");
        }

        @NotNull
        private Map<String, String> doPropagate(BaggageImpl baggage) {
            Map<String, String> resultHeaders = new HashMap<>();
            W3CBaggagePropagation.propagate(baggage, resultHeaders, TextHeaderMapAccessor.INSTANCE);

            Map<String, String> utf8Headers = new HashMap<>();
            W3CBaggagePropagation.propagate(baggage, utf8Headers, Utf8HeaderMapAccessor.INSTANCE);
            assertThat(resultHeaders).isEqualTo(utf8Headers);

            return resultHeaders;
        }

        @Test
        public void testEmptyBaggage() {
            BaggageImpl baggage = BaggageImpl.builder().build();

            Map<String, String> resultHeaders = doPropagate(baggage);

            assertThat(resultHeaders).hasSize(0);
        }

        @Test
        public void testInvalidKeysIgnored() {
            BaggageImpl baggage = BaggageImpl.builder()
                .put("foo", "bar")
                .put("bad,key", "42")
                .build();

            Map<String, String> resultHeaders = doPropagate(baggage);

            assertThat(resultHeaders)
                .hasSize(1)
                .containsEntry("baggage", "foo=bar");
        }


        @Test
        public void testOnlyInvalidKeys() {
            BaggageImpl baggage = BaggageImpl.builder()
                .put("bad,key", "42")
                .build();

            Map<String, String> resultHeaders = doPropagate(baggage);

            assertThat(resultHeaders).hasSize(0);
        }
    }


    @Nested
    public class Parsing {

        @Test
        public void testComplexValueString() {
            String[] baggageHeaders = {
                "foo=bar,my_key!=hello%2C%20world!%3F%25%C3%A4%3D;metadata=blub,,bar=baz;override=me,foo=bar2;overridden=meta",
                "bar=baz2"
            };

            BaggageImpl.Builder resultBuilder = BaggageImpl.builder();
            W3CBaggagePropagation.parse(baggageHeaders, new TextBaggageheaderGetter(), resultBuilder);

            assertThat(resultBuilder.build())
                .hasSize(3)
                .containsEntry("my_key!", "hello, world!?%ä=", "metadata=blub")
                .containsEntry("bar", "baz2", null)
                .containsEntry("foo", "bar2", "overridden=meta");

            BaggageImpl.Builder utf8ResultBuilder = BaggageImpl.builder();
            W3CBaggagePropagation.parse(baggageHeaders, new UTF8BaggageheaderGetter(), utf8ResultBuilder);
            assertThat(utf8ResultBuilder.build()).isEqualTo(resultBuilder.build());
        }


        @Test
        public void testNoValue() {
            BaggageImpl.Builder textResultBuilder = BaggageImpl.builder();
            W3CBaggagePropagation.parse(Collections.emptyMap(), TextHeaderMapAccessor.INSTANCE, textResultBuilder);
            assertThat(textResultBuilder.build()).hasSize(0);

            BaggageImpl.Builder utf8ResultBuilder = BaggageImpl.builder();
            W3CBaggagePropagation.parse(Collections.emptyMap(), Utf8HeaderMapAccessor.INSTANCE, utf8ResultBuilder);
            assertThat(textResultBuilder.build()).hasSize(0);
        }

    }

    private static class TextBaggageheaderGetter implements TextHeaderGetter<String[]> {
        @Nullable
        @Override
        public String getFirstHeader(String headerName, String[] baggageHeaders) {
            if (headerName.equals("baggage")) {
                return baggageHeaders[0];
            }
            return null;
        }

        @Override
        public <S> void forEach(String headerName, String[] baggageHeaders, S state, HeaderConsumer<String, S> consumer) {
            if (headerName.equals("baggage")) {
                for (String val : baggageHeaders) {
                    consumer.accept(val, state);
                }
            }
        }
    }


    private static class UTF8BaggageheaderGetter implements UTF8ByteHeaderGetter<String[]> {
        @Nullable
        @Override
        public byte[] getFirstHeader(String headerName, String[] baggageHeaders) {
            if (headerName.equals("baggage")) {
                return baggageHeaders[0].getBytes(StandardCharsets.UTF_8);
            }
            return null;
        }

        @Override
        public <S> void forEach(String headerName, String[] baggageHeaders, S state, HeaderConsumer<byte[], S> consumer) {
            if (headerName.equals("baggage")) {
                for (String val : baggageHeaders) {
                    consumer.accept(val.getBytes(StandardCharsets.UTF_8), state);
                }
            }
        }
    }

}
