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
package co.elastic.apm.agent.impl.context;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import specs.TestJsonSpec;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;

public class ServiceResourceTest {

    private static Transaction root;

    @BeforeAll
    static void startRootTransaction() {
        ElasticApmTracer tracer = MockTracer.createRealTracer();
        root = Objects.requireNonNull(tracer.startRootTransaction(null));
    }

    @AfterAll
    static void endTransaction() {
        root.end();
    }

    @ParameterizedTest
    @MethodSource("getTestCases")
    void testServiceResourceInference(JsonNode testCase) {
        Span span = createSpan(testCase);

        // increment reference count to prevent recycling while test executes
        span.incrementReferences();

        // auto-inference happens now
        span.end();

        String expected = getTextValueOrNull(testCase, "expected_resource");
        if (expected == null) {
            // no resource nor any target fields is expected
            assertThat(span.getContext().getServiceTarget()).isEmpty();
        } else {
            assertThat(span.getContext().getServiceTarget())
                .describedAs(getTextValueOrNull(testCase, "failure_message"))
                .hasDestinationResource(expected);
        }
        span.decrementReferences();
    }

    private Span createSpan(JsonNode testCase) {
        Span span = root.createSpan();
        JsonNode spanJson = testCase.get("span");
        span.withType(spanJson.get("type").textValue());
        JsonNode subtypeJsonNode = spanJson.get("subtype");
        if (subtypeJsonNode != null) {
            span.withSubtype(subtypeJsonNode.textValue());
        }
        if (spanJson.get("exit").asBoolean(false)) {
            span.asExit();
        }
        JsonNode contextJson = spanJson.get("context");
        if (contextJson != null) {
            SpanContext context = span.getContext();
            JsonNode dbJson = contextJson.get("db");
            if (dbJson != null) {
                Db db = context.getDb();
                db.withType(getTextValueOrNull(dbJson, "type"));
                db.withInstance(getTextValueOrNull(dbJson, "instance"));
            }
            JsonNode messageJson = contextJson.get("message");
            if (messageJson != null) {
                Message message = context.getMessage();
                message.withBody(getTextValueOrNull(messageJson, "body"));
                JsonNode queueJson = messageJson.get("queue");
                if (queueJson != null) {
                    message.withQueue(queueJson.get("name").asText());
                }
            }
            JsonNode httpJson = contextJson.get("http");
            if (httpJson != null) {
                JsonNode urlJson = httpJson.get("url");
                if (urlJson != null) {
                    Url url = context.getHttp().getInternalUrl();
                    url.withHostname(getTextValueOrNull(urlJson, "host"));
                    JsonNode portJson = urlJson.get("port");
                    if (portJson != null) {
                        url.withPort(portJson.intValue());
                    }
                }
            }
            JsonNode destinationJson = contextJson.get("destination");
            if (destinationJson != null) {
                JsonNode serviceJson = destinationJson.get("service");
                if (serviceJson != null) {
                    String resource = getTextValueOrNull(serviceJson, "resource");
                    if (resource != null) {
                        // we use the user-provided resource to emulate an existing resource value
                        context.getServiceTarget().withUserDestinationResource(resource);
                    }
                }
            }
        }
        return span;
    }

    @Nullable
    private String getTextValueOrNull(JsonNode dbJson, String type) {
        JsonNode jsonNode = dbJson.get(type);
        if (jsonNode == null || jsonNode.isNull()) {
            return null;
        }
        return jsonNode.asText();
    }

    private static Stream<JsonNode> getTestCases() {
        Iterator<JsonNode> json = TestJsonSpec.getJson("service_resource_inference.json").iterator();
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(json, Spliterator.ORDERED), false);
    }
}
