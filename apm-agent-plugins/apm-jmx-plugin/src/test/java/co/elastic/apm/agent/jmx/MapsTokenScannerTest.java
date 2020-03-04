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
package co.elastic.apm.agent.jmx;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapsTokenScannerTest {

    @Test
    void testSimpleScan() {
        MapsTokenScanner scanner = new MapsTokenScanner("foo[bar]");
        assertThat(scanner.scanKey()).isEqualTo("foo");
        assertThat(scanner.peek()).isEqualTo('[');
        assertThat(scanner.scanValue()).isEqualTo("bar");
    }

    @Test
    void testMultiScan() {
        for (String input : List.of("foo[bar] baz[qux]", "foo[bar]baz[qux]", "foo[bar] baz[qux] ")) {
            MapsTokenScanner scanner = new MapsTokenScanner(input);
            assertThat(scanner.scanKey()).isEqualTo("foo");
            assertThat(scanner.scanValue()).isEqualTo("bar");
            scanner.skipWhiteSpace();
            assertThat(scanner.scanKey()).isEqualTo("baz");
            assertThat(scanner.scanValue()).isEqualTo("qux");
        }
    }

    @Test
    void testScanMap() {
        for (String input : List.of("foo[bar] baz[qux]", "foo[bar]baz[qux]", "foo[bar] baz[qux] ", "foo[bar] baz[qux], quux[corge]")) {
            assertThat(new MapsTokenScanner(input).scanMap()).isEqualTo(Map.of("foo", "bar", "baz", "qux"));
        }
    }

    @Test
    void testScanMaps() {
        assertThat(new MapsTokenScanner("f[b]").scanMaps())
            .isEqualTo(List.of(Map.of("f", "b")));
        assertThat(new MapsTokenScanner("foo bar[baz,qux ]").scanMaps())
            .isEqualTo(List.of(Map.of("foo bar", "baz,qux ")));
        assertThat(new MapsTokenScanner(" foo[bar] baz[qux] ").scanMaps())
            .isEqualTo(List.of(Map.of("foo", "bar", "baz", "qux")));

        assertThat(new MapsTokenScanner(" foo[bar] baz[qux], quux[corge] ").scanMaps())
            .isEqualTo(List.of(Map.of("foo", "bar", "baz", "qux"), Map.of("quux", "corge")));
        assertThat(new MapsTokenScanner(" foo[bar] baz[qux]  ,  quux[corge] ").scanMaps())
            .isEqualTo(List.of(Map.of("foo", "bar", "baz", "qux"), Map.of("quux", "corge")));
    }

    @Test
    void testScanMultiValueMaps() {
        assertThat(new MapsTokenScanner("a[a] a[b]").scanMultiValueMaps())
            .isEqualTo(List.of(Map.of("a", List.of("a", "b"))));
    }

    @Test
    void testMissingValue() {
        assertThatThrownBy(() -> new MapsTokenScanner("foo").scanMap())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Expected value start");
    }

    @Test
    void testMissingKey() {
        assertThatThrownBy(() -> new MapsTokenScanner("foo[bar] [qux]").scanMap())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Empty key");
    }

    @Test
    void testMissingClosingBracket() {
        assertThatThrownBy(() -> new MapsTokenScanner("foo[bar").scanMap())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Expected end value token ']'");
    }

    @Test
    void testBracketWithinValue() {
        assertThatThrownBy(() -> new MapsTokenScanner("foo[b[a]r]").scanMap())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid char '[' within a value");
    }

    @Test
    void testConversionRoundtrip() {
        List<Map<String, List<String>>> input = List.of(Map.of("foo", List.of("bar"), "baz", List.of("qux")), Map.of(), Map.of("quux", List.of("corge")));
        assertThat(new MapsTokenScanner(MapsTokenScanner.toTokenString(input)).scanMultiValueMaps()).isEqualTo(input);
    }

}
