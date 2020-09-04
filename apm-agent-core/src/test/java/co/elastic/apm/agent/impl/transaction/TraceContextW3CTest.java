/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.mock;

public class TraceContextW3CTest {

    private JsonNode testData;


    @BeforeEach
    void setUp() throws IOException {
        testData = new ObjectMapper()
            .enable(JsonParser.Feature.ALLOW_COMMENTS)
            .readTree(getClass().getResource("/w3c_test_data.json"));
    }

    @Test
    void testW3CData() {
        assertSoftly(softly -> {
            for (JsonNode testCase : testData) {
                PotentiallyMultiValuedMap headersMap = getHeaders(testCase.get("headers"));
                if (headersMap.getAll("traceparent").size() == 1) {
                    final String traceParentHeader = headersMap.getFirst("traceparent");
                    assertThat(traceParentHeader).isNotNull();
                    final boolean traceparentValid = testCase.get("is_traceparent_valid").booleanValue();
                    final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
                    softly.assertThat(traceContext.asChildOf(traceParentHeader))
                        .withFailMessage("Expected '%s' to be %s", traceParentHeader, traceparentValid ? "valid" : "invalid")
                        .isEqualTo(traceparentValid);

                }
            }
        });
    }

    private PotentiallyMultiValuedMap getHeaders(JsonNode headers) {
        final PotentiallyMultiValuedMap map = new PotentiallyMultiValuedMap();
        for (JsonNode header : headers) {
            map.add(header.get(0).textValue(), header.get(1).textValue());
        }
        return map;
    }
}
