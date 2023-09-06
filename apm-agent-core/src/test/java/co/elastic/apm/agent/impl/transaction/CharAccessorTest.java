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

import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.stream.Stream;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CharAccessorTest {

    @Retention(RetentionPolicy.RUNTIME)
    @ParameterizedTest(name = "{index} {0}")
    @MethodSource("testArguments")
    @interface TestForAllAccessors {
    }

    private static Stream<Arguments> testArguments() {
        return Stream.of(
            Arguments.of(Named.of("CharSequence", CharAccessor.forCharSequence()), (Function<String, CharSequence>) (input) -> input),
            Arguments.of(Named.of("ascii byte[]", CharAccessor.forAsciiBytes()), (Function<String, byte[]>) (input) -> input.getBytes(StandardCharsets.US_ASCII))
        );
    }

    @TestForAllAccessors
    <T> void testLength(CharAccessor<T> accessor, Function<String, T> inputConverter) {
        assertThat(accessor.length(inputConverter.apply(""))).isEqualTo(0);
        assertThat(accessor.length(inputConverter.apply("abc"))).isEqualTo(3);
    }

    @TestForAllAccessors
    <T> void testCharAt(CharAccessor<T> accessor, Function<String, T> inputConverter) {
        T input = inputConverter.apply("abcde");
        assertThat(accessor.charAt(input, 0)).isEqualTo('a');
        assertThat(accessor.charAt(input, 2)).isEqualTo('c');
        assertThat(accessor.charAt(input, 4)).isEqualTo('e');
        assertThatThrownBy(() -> accessor.charAt(input, -1));
        assertThatThrownBy(() -> accessor.charAt(input, 5));
    }

    @TestForAllAccessors
    <T> void testAsString(CharAccessor<T> accessor, Function<String, T> inputConverter) {
        assertThat(accessor.asString(inputConverter.apply(""))).isEqualTo("");
        assertThat(accessor.asString(inputConverter.apply("abc"))).isEqualTo("abc");
    }

    @TestForAllAccessors
    <T> void testHexByteDecode(CharAccessor<T> accessor, Function<String, T> inputConverter) {
        assertThat(accessor.readHexByte(inputConverter.apply("0a"), 0)).isEqualTo((byte) 0x0a);
        assertThat(accessor.readHexByte(inputConverter.apply("ff"), 0)).isEqualTo((byte) 0xFF);
        assertThat(accessor.readHexByte(inputConverter.apply("zz23"), 2)).isEqualTo((byte) 0x23);

        assertThatThrownBy(() -> accessor.readHexByte(inputConverter.apply("ab"), -1));
        assertThatThrownBy(() -> accessor.readHexByte(inputConverter.apply("a"), 0));
        assertThatThrownBy(() -> accessor.readHexByte(inputConverter.apply("ab"), 1));
        assertThatThrownBy(() -> accessor.readHexByte(inputConverter.apply("1g"), 0));
    }

    @TestForAllAccessors
    <T> void testHexByteArrayDecode(CharAccessor<T> accessor, Function<String, T> inputConverter) {

        T input = inputConverter.apply("0123456789abcdef");

        byte[] readFull = new byte[8];
        accessor.readHex(input, 0, readFull);
        assertThat(readFull).containsExactly(0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF);

        byte[] readPartial = new byte[2];
        accessor.readHex(input, 3, readPartial);
        assertThat(readPartial).containsExactly(0x34, 0x56);

        assertThatThrownBy(() -> accessor.readHex(input, 1, new byte[8]));
        assertThatThrownBy(() -> accessor.readHex(input, -1, new byte[8]));
    }

    @TestForAllAccessors
    <T> void testLeadingWhitespaceCounting(CharAccessor<T> accessor, Function<String, T> inputConverter) {
        assertThat(accessor.getLeadingWhitespaceCount(inputConverter.apply(""))).isEqualTo(0);
        assertThat(accessor.getLeadingWhitespaceCount(inputConverter.apply(" \t\r\nabcd"))).isEqualTo(4);
        assertThat(accessor.getLeadingWhitespaceCount(inputConverter.apply(" a\t\r\n"))).isEqualTo(1);
        assertThat(accessor.getLeadingWhitespaceCount(inputConverter.apply("a\t\r\n"))).isEqualTo(0);
    }

    @TestForAllAccessors
    <T> void testTrailingWhitespaceCounting(CharAccessor<T> accessor, Function<String, T> inputConverter) {
        assertThat(accessor.getTrailingWhitespaceCount(inputConverter.apply(""))).isEqualTo(0);
        assertThat(accessor.getTrailingWhitespaceCount(inputConverter.apply("abcd \t\r\n"))).isEqualTo(4);
        assertThat(accessor.getTrailingWhitespaceCount(inputConverter.apply("\t\r\na "))).isEqualTo(1);
        assertThat(accessor.getTrailingWhitespaceCount(inputConverter.apply("\t\r\na"))).isEqualTo(0);
    }

    @TestForAllAccessors
    <T> void testContainsAtOffset(CharAccessor<T> accessor, Function<String, T> inputConverter) {

        assertThat(accessor.containsAtOffset(inputConverter.apply(""), 0, "")).isTrue();

        T abc = inputConverter.apply("abc");
        assertThat(accessor.containsAtOffset(abc, 0, "abc")).isTrue();
        assertThat(accessor.containsAtOffset(abc, 1, "bc")).isTrue();
        assertThat(accessor.containsAtOffset(abc, 0, "ab")).isTrue();
        assertThat(accessor.containsAtOffset(abc, 1, "b")).isTrue();

        assertThat(accessor.containsAtOffset(abc, 1, "a")).isFalse();
        assertThat(accessor.containsAtOffset(abc, 1, "bcd")).isFalse();
        assertThat(accessor.containsAtOffset(abc, 0, "A")).isFalse();
        assertThat(accessor.containsAtOffset(abc, 0, "abcd")).isFalse();

        assertThatThrownBy(() -> accessor.containsAtOffset(inputConverter.apply("a"), 2, ""));
        assertThatThrownBy(() -> accessor.containsAtOffset(inputConverter.apply("a"), -1, ""));
    }

}
