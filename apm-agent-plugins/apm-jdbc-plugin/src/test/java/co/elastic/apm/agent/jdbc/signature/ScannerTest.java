/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.jdbc.signature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScannerTest {

    private Scanner scanner;
    private JsonNode testCases;

    @BeforeEach
    void setUp() throws Exception {
        scanner = new Scanner();
        testCases = new ObjectMapper().readTree(getClass().getResource("/scanner_tests.json"));
    }

    @Test
    void testScanner() {
        for (JsonNode testCase : testCases) {
            System.out.println(testCase);
            scanner.setQuery(testCase.get("input").textValue());
            for (JsonNode token : testCase.get("tokens")) {
                assertThat(scanner.scan()).isEqualTo(Scanner.Token.valueOf(token.get("kind").textValue()));
                assertThat(scanner.text()).isEqualTo(token.get("text").textValue());
            }
        }
    }
}
