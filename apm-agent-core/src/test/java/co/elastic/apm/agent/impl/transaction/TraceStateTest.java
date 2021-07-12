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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void trySetMultipleTimes_set_first() {
        trySetMultipleTimes((s, h) -> traceState.set(s, h));
    }

    @Test
    void trySetMultipleTimes_header_first() {
        trySetMultipleTimes((s, h) -> traceState.addTextHeader(h));
    }

    private void trySetMultipleTimes(BiConsumer<Double, String> firstSet) {
        double sampleRate = 0.5d;
        String header = TraceState.getHeaderValue(sampleRate);
        firstSet.accept(sampleRate, header);
        checkHeader(0.5d, header);

        // calling set more than once is a but within agent, thus we fail with exception
        assertThatThrownBy(() -> traceState.set(0.5d, header));

        // while setting with header is also an error, we just ignore it
        traceState.addTextHeader(TraceState.getHeaderValue(0.7d));
        checkHeader(0.5d, header);
    }

    @Test
    void getHeaderValue() {
        assertThatThrownBy(() -> TraceState.getHeaderValue(Double.NaN));
        assertThatThrownBy(() -> TraceState.getHeaderValue(-1d));
        assertThatThrownBy(() -> TraceState.getHeaderValue(1.1d));
        assertThat(TraceState.getHeaderValue(0d)).isEqualTo("es=s:0");
        assertThat(TraceState.getHeaderValue(1d)).isEqualTo("es=s:1");
        assertThat(TraceState.getHeaderValue(0.5d)).isEqualTo("es=s:0.5");
        assertThat(TraceState.getHeaderValue(0.55555d)).isEqualTo("es=s:0.5556");
        assertThat(TraceState.getHeaderValue(0.0000001d)).isEqualTo("es=s:0.0001");
    }

    @Test
    void multipleVendorsInSameHeader() {
        traceState.addTextHeader("aa=1|2|3,es=s:0.5,bb=4|5|6");

        checkHeader(0.5d, "aa=1|2|3,es=s:0.5,bb=4|5|6");
    }

    @Test
    void otherVendorsOnly() {
        traceState.addTextHeader("aa=1|2|3");
        traceState.addTextHeader("bb=4|5|6");

        checkHeader(Double.NaN, "aa=1|2|3,bb=4|5|6");
    }

    @Test
    void sampleRateFromHeaders() {
        traceState.addTextHeader("aa=1|2|3");
        traceState.addTextHeader("es=s:0.5");
        traceState.addTextHeader("bb=4|5|6");
        checkHeader(0.5d, "aa=1|2|3,es=s:0.5,bb=4|5|6");
    }

    @Test
    void sampleRateAddedToHeaders() {
        traceState.addTextHeader("aa=1|2|3");
        traceState.addTextHeader("bb=4|5|6");
        traceState.set(0.444d, TraceState.getHeaderValue(0.444d));

        checkHeader(0.444d, "aa=1|2|3,bb=4|5|6,es=s:0.444");
    }

    @Test
    void multipleSampleRateHeaders_multiple_headers() {
        traceState.addTextHeader("es=s:0.444");
        traceState.addTextHeader("es=s:0.333");
        traceState.addTextHeader("aa=1|2|3,es=s:0.555,bb=4|5|6");

        checkHeader(0.444d, "es=s:0.444,aa=1|2|3,bb=4|5|6");
    }

    @Test
    void buildCopy() {
        TraceState other = new TraceState();

        other.set(0.2d, TraceState.getHeaderValue(0.2d));
        other.addTextHeader("aa=1_2");

        traceState.copyFrom(other);

        other.resetState();

        checkHeader(0.2d, "es=s:0.2,aa=1_2");
    }

    @ParameterizedTest
    @CsvSource(delimiterString = "|", value = {
        "es=k:0;s:0.555555,aa=123|es=k:0;s:0.5556,aa=123",
        "es=s:0.555555;k:0,aa=123|es=s:0.5556;k:0,aa=123",
        "es=k:0,aa=123|aa=123"
    })
    void unknownKeysAreIgnored(String header, String rewrittenHeader) {
        traceState.addTextHeader(header);

        checkHeader(header.contains("s:") ? 0.5556d : Double.NaN, rewrittenHeader);
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

        checkHeader(Double.NaN, null);
    }

    @ParameterizedTest
    @CsvSource({
        "0.00000001,0.0001", // less than precision but more than zero should be rounded to minimal precision
        "0.55554,0.5555",
        "0.55555,0.5556",
        "0.55556,0.5556"})
    void appliesRoundingOnUpstreamHeader(String headerRate, String expectedRate) {
        traceState.addTextHeader("es=s:" + headerRate);
        assertThat(traceState.getSampleRate()).isEqualTo(Double.parseDouble(expectedRate));
        String header = "es=s:" + expectedRate;
        assertThat(traceState.toTextHeader()).isEqualTo(header);
        assertThat(TraceState.getHeaderValue(traceState.getSampleRate())).isEqualTo(header);
    }

    private void checkHeader(double expectedSampleRate, @Nullable String expectedHeader) {
        double sampleRate = traceState.getSampleRate();
        if (Double.isNaN(expectedSampleRate)) {
            assertThat(sampleRate).isNaN();
        } else {
            assertThat(sampleRate).isEqualTo(expectedSampleRate);
        }

        assertThat(traceState.toTextHeader()).isEqualTo(expectedHeader);
    }

    private void checkSingleHeader(double rate) {
        checkHeader(rate, TraceState.getHeaderValue(rate));
    }
}
