/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.db.signature;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import specs.TestJsonSpec;

import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class ScannerTest {

    private Scanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new Scanner();
    }

    @ParameterizedTest
    @MethodSource("getTestCases")
    void testScanner(JsonNode testCase) {
        scanner.setQuery(testCase.get("input").textValue());

        String comment = Optional.ofNullable(testCase.get("comment"))
            .map(JsonNode::asText)
            .orElse(null);

        JsonNode tokens = testCase.get("tokens");
        if (tokens != null) {
            for (JsonNode token : tokens) {
                assertThat(scanner.scan())
                    .describedAs(comment)
                    .isEqualTo(Scanner.Token.valueOf(token.get("kind").textValue()));

                assertThat(scanner.text())
                    .describedAs(comment)
                    .isEqualTo(token.get("text").textValue());
            }
        }

        assertThat(scanner.scan())
            .describedAs(comment)
            .isEqualTo(Scanner.Token.EOF);

    }

    private static Stream<JsonNode> getTestCases() {
        Iterator<JsonNode> json = TestJsonSpec.getJson("sql_token_examples.json").iterator();
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(json, Spliterator.ORDERED), false);
    }
}
