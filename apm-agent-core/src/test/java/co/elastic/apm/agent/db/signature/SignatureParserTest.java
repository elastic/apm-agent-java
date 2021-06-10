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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import specs.TestJsonSpec;

import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

public class SignatureParserTest {

    protected SignatureParser signatureParser = new SignatureParser();

    @ParameterizedTest
    @MethodSource("getTestSignatures_shared")
    protected void testSignature_shared(String input, String output, String comment) {
        final StringBuilder signature = new StringBuilder();
        signatureParser.querySignature(input, signature, false);
        assertThat(signature.toString())
            .describedAs(comment)
            .isEqualTo(output);
    }

    private static Stream<Arguments> getTestSignatures_shared() {
        return parseTestParameters(TestJsonSpec.getJson("sql_signature_examples.json"));
    }

    @ParameterizedTest
    @MethodSource("getTestSignatures_java")
    void testSignature_java(String input, String output, String comment) {
        testSignature_shared(input, output, comment);
    }

    private static Stream<Arguments> getTestSignatures_java() {
        // this file has the same format as the shared variant, but with cases only relevant in java
        // for example, some JDBC-only syntax that aren't used anywhere else
        return parseTestParameters(TestJsonSpec.getJson(SignatureParserTest.class, "signature_tests.json"));
    }

    protected static Stream<Arguments> parseTestParameters(JsonNode json) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(json.iterator(), Spliterator.ORDERED), false)
            .map(node -> {
                String input = node.get("input").asText();
                String output = node.get("output").asText();
                String comment = Optional.ofNullable(node.get("comment"))
                    .map(JsonNode::asText)
                    .orElse(null);
                return Arguments.of(input, output, comment);
            });
    }

    @Test
    void testDbLinkForUpdate() {
        final StringBuilder sb = new StringBuilder();
        final StringBuilder dblink = new StringBuilder();
        signatureParser.querySignature("UPDATE foo.bar@\"DBLINK.FQDN.COM@USER\" SET bar=1 WHERE baz=2", sb, dblink, false);
        assertThat(dblink.toString()).isEqualTo("DBLINK.FQDN.COM@USER");
    }

    @Test
    void testDbLink() {
        final StringBuilder sb = new StringBuilder();
        final StringBuilder dblink = new StringBuilder();
        signatureParser.querySignature("SELECT * FROM TABLE1@DBLINK", sb, dblink, false);
        assertThat(dblink.toString()).isEqualTo("DBLINK");
    }

    @Test
    void testDbLinkCache() {
        final StringBuilder sb = new StringBuilder();
        final StringBuilder dblink = new StringBuilder();
        signatureParser.querySignature("SELECT * FROM TABLE1@DBLINK", sb, dblink, true);
        assertThat(dblink.toString()).isEqualTo("DBLINK");
        sb.setLength(0);
        dblink.setLength(0);
        signatureParser.querySignature("SELECT * FROM TABLE1@DBLINK", sb, dblink, true);
        assertThat(dblink.toString()).isEqualTo("DBLINK");
    }

    @Test
    void testDbLinkFqdn() {
        final StringBuilder sb = new StringBuilder();
        final StringBuilder dblink = new StringBuilder();
        signatureParser.querySignature("SELECT * FROM TABLE1@\"DBLINK.FQDN.COM\"", sb, dblink, false);
        assertThat(dblink.toString()).isEqualTo("DBLINK.FQDN.COM");
    }

    @Test
    void testDbLinkFqdnWithUser() {
        final StringBuilder sb = new StringBuilder();
        final StringBuilder dblink = new StringBuilder();
        signatureParser.querySignature("SELECT * FROM TABLE1@\"DBLINK.FQDN.COM@USER\"", sb, dblink, false);
        assertThat(dblink.toString()).isEqualTo("DBLINK.FQDN.COM@USER");
    }

}
