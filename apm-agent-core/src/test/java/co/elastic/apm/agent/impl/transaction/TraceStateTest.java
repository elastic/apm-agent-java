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
        assertThat(traceState.getSampleRate()).isNull();
    }

    @Test
    void createEmpty() {
        assertThat(traceState.toTextHeader()).isNull();
        assertThat(traceState.getSampleRate()).isNull();
    }

    @Test
    void addSampleRate() {
        traceState.setSampleRate(0.5d);
        assertThat(traceState.getSampleRate()).isEqualTo(0.5d);
        assertThat(traceState.toTextHeader()).isEqualTo("es=s:0.5");
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
        assertThat(traceState.getSampleRate()).isNull();
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

        traceState.setSampleRate(0.444d);
        assertThat(traceState.getSampleRate()).isEqualTo(0.444d);
        assertThat(traceState.toTextHeader()).isEqualTo("aa=1|2|3,bb=4|5|6,es=s:0.444");
    }

    @Test
    void invalidSampleRate() {
        traceState.addTextHeader("es=s:aa");
        assertThat(traceState.getSampleRate()).isNull();
    }

    @Test
    void buildCopy() {
        TraceState other = new TraceState();
        other.setSampleRate(0.2d);
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
    @CsvSource({"-1", "2"})
    void outOfBoundsSampleRateIsIgnored(int value) {
        traceState.addTextHeader("es=s:" + value);
        assertThat(traceState.getSampleRate()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
        "0.55554,0.5555",
        "0.55555,0.5556",
        "0.55556,0.5556"})
    void appliesRoundingOnUpstreamHeader(String headerRate, Double expectedRate) {
        traceState.addTextHeader("es=s:" + headerRate);
        assertThat(traceState.getSampleRate()).isEqualTo(expectedRate);
        assertThat(traceState.toTextHeader()).isEqualTo("es=s:" + expectedRate);
    }

    @Test
    void reuseHeaderValueIfNotRewriting() {
        String headerValue = "es=s:0.43";
        traceState.addTextHeader(headerValue);
        assertThat(traceState.getSampleRate()).isEqualTo(0.43d);
        assertThat(traceState.toTextHeader()).isSameAs(headerValue);
    }

    @Test
    void useCacheWithSameSampleRateAfterRecycling() {
        traceState.setSampleRate(0.43d);
        assertThat(traceState.getSampleRate()).isEqualTo(0.43d);

        String cachedValue = traceState.toTextHeader();
        assertThat(cachedValue).isEqualTo("es=s:0.43");

        traceState.resetState();
        traceState.setSampleRate(0.43d);
        assertThat(traceState.toTextHeader()).isSameAs(cachedValue);
    }

}
