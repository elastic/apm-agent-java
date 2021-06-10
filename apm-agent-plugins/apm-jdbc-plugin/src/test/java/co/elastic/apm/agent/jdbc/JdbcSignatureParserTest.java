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
package co.elastic.apm.agent.jdbc;

import co.elastic.apm.agent.db.signature.Scanner;
import co.elastic.apm.agent.db.signature.SignatureParser;
import co.elastic.apm.agent.db.signature.SignatureParserTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import specs.TestJsonSpec;

import java.util.stream.Stream;

class JdbcSignatureParserTest extends SignatureParserTest {

    @BeforeEach
    void setUp() {
        this.signatureParser = new SignatureParser(() -> new Scanner(new JdbcFilter()));
    }

    @ParameterizedTest
    @MethodSource("getTestSignatures_java")
    void testSignature_jdbc(String input, String output, String comment) {
        testSignature_shared(input, output, comment);
    }

    private static Stream<Arguments> getTestSignatures_java() {
        // this file has the same format as the shared variant, but with cases only relevant in java
        // for example, some JDBC-only syntax that aren't used anywhere else
        return parseTestParameters(TestJsonSpec.getJson(JdbcSignatureParserTest.class, "jdbc_signature_tests.json"));
    }

}
