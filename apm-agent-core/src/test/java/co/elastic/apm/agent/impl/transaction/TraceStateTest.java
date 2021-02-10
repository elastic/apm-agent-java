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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.annotation.Nullable;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class TraceStateTest {

    private TraceState traceState = null;

    @BeforeEach
    void init() {
        traceState = new TraceState();
    }

    @AfterEach
    void testCleanup() {
        // check for proper reset at end of each test with current state
        traceState.resetState();

        assertThat(traceState.toTextHeader()).isNull();
        assertThat(traceState.getSampleRate()).isNaN();
    }

    @Test
    void createEmpty() {
        assertThat(traceState.toTextHeader()).isNull();
        assertThat(traceState.getSampleRate()).isNaN();
    }

    @ParameterizedTest
    @CsvSource(delimiterString = "|", value = {
        // corner cases
        "0|one=two|",
        "7|one=two|one=two",
        //
        "20|one=two_three=four|one=two,three=four", // no overflow
        "20|one=two_three=four_five=six_seven=eight|one=two,three=four", // overflow after 'four'
        "20|one=two,three=four_five=six,seven=eight|one=two,three=four", // overflow within 2cnd header
        "20|one=two_three=four_five=six,seven=eight|one=two,three=four", // overflow within first header
        "20|one=two_three=four,X,five=six_seven=eight,nine=ten|one=two,three=four,X", // empty entry kept as-is
        "20|one=two_three=four,five=six,seven=eight_nine=ten|one=two,three=four", // multiple overflow values
        "18|one=two_three=four,five=six,seven=eight_nine=ten|one=two,three=four", // cutoff on separator,
        "17|one=two_three=four,five=six,seven=eight_nine=ten,eleven-twelve|one=two,nine=ten", // just fits
    })
    void sizeLimit(int limit, String headers, @Nullable String expected) {
        Function<String, String> replaceSpaces = s -> s == null ? null : s.replace('X', ' ');

        traceState.setSizeLimit(limit);
        for (String h : headers.split("_")) {
            traceState.addTextHeader(replaceSpaces.apply(h));
        }
        assertThat(traceState.toTextHeader()).isEqualTo(replaceSpaces.apply(expected));

        // none of those values should have a sample rate set
        assertThat(traceState.getSampleRate()).isNaN();
    }

    @Test
    void addSampleRate() {
        String headerValue = TraceState.getHeaderValue(0.5d);
        assertThat(headerValue).isEqualTo("es=s:0.5");
        traceState.set(0.5d, headerValue);
        assertThat(traceState.getSampleRate()).isEqualTo(0.5d);

        assertThat(traceState.toTextHeader())
            .describedAs("should reuse the same string without allocating a new one")
            .isSameAs(headerValue);
    }

    @Test
    void multipleVendorsInSameHeader() {
        traceState.addTextHeader("aa=1|2|3,es=s:0.5,bb=4|5|6");
        assertThat(traceState.getSampleRate()).isEqualTo(0.5d);
        assertThat(traceState.toTextHeader()).isEqualTo("aa=1|2|3,es=s:0.5,bb=4|5|6");
    }

    @Test
    void otherVendorsOnly() {
        traceState.addTextHeader("aa=1|2|3");
        traceState.addTextHeader("bb=4|5|6");
        assertThat(traceState.toTextHeader()).isEqualTo("aa=1|2|3,bb=4|5|6");
        assertThat(traceState.getSampleRate()).isNaN();
    }

    @Test
    void sampleRateFromHeaders() {
        traceState.addTextHeader("aa=1|2|3");
        traceState.addTextHeader("es=s:0.5");
        assertThat(traceState.getSampleRate()).isEqualTo(0.5d);
        traceState.addTextHeader("bb=4|5|6");
        assertThat(traceState.toTextHeader()).isEqualTo("aa=1|2|3,es=s:0.5,bb=4|5|6");
    }

    @Test
    void sampleRateAddedToHeaders() {
        traceState.addTextHeader("aa=1|2|3");
        traceState.addTextHeader("bb=4|5|6");

        traceState.set(0.444d, TraceState.getHeaderValue(0.444d));
        assertThat(traceState.getSampleRate()).isEqualTo(0.444d);
        assertThat(traceState.toTextHeader()).isEqualTo("aa=1|2|3,bb=4|5|6,es=s:0.444");
    }

    @Test
    void buildCopy() {
        TraceState other = new TraceState();

        other.set(0.2d, TraceState.getHeaderValue(0.2d));
        other.addTextHeader("aa=1_2");

        traceState.copyFrom(other);

        other.resetState();

        assertThat(traceState.getSampleRate()).isEqualTo(0.2d);
        assertThat(traceState.toTextHeader()).isEqualTo("es=s:0.2,aa=1_2");
    }

    @ParameterizedTest
    @CsvSource(value = {
        "es=k:0;s:0.555555,aa=123|es=k:0;s:0.5556,aa=123",
        "es=s:0.555555;k:0,aa=123|es=s:0.5556;k:0,aa=123"},
        delimiterString = "|")
    void unknownKeysAreIgnored(String header, String rewrittenHeader) {
        traceState.addTextHeader(header);
        assertThat(traceState.getSampleRate()).isEqualTo(0.5556d);
        assertThat(traceState.toTextHeader()).isEqualTo(rewrittenHeader);
    }

    @ParameterizedTest
    @CsvSource({
        "es=",
        "es=s:",
        "es=s:-1",
        "es=s:2",
        "es=s:aa"
    })
    void invalidValuesIgnored(String header) {
        traceState.addTextHeader(header);
        assertThat(traceState.getSampleRate()).isNaN();
    }

    @ParameterizedTest
    @CsvSource({
        "0.00001,0.0001", // less than precision but more than zero should be rounded to minimal precision
        "0.55554,0.5555",
        "0.55555,0.5556",
        "0.55556,0.5556"})
    void appliesRoundingOnUpstreamHeader(String headerRate, Double expectedRate) {
        traceState.addTextHeader("es=s:" + headerRate);
        assertThat(traceState.getSampleRate()).isEqualTo(expectedRate);
        assertThat(traceState.toTextHeader()).isEqualTo("es=s:" + expectedRate);
    }

}
