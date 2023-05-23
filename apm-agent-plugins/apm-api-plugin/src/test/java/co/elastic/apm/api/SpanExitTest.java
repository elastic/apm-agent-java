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
package co.elastic.apm.api;

import co.elastic.apm.AbstractApiTest;
import co.elastic.apm.agent.impl.transaction.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;


public class SpanExitTest extends AbstractApiTest {

    @BeforeEach
    void before() {
        // user-provided spans are not expected to fit the general requirements for exit spans
        reporter.disableCheckStrictSpanType();
        reporter.disableCheckDestinationAddress();
    }

    // @CaptureSpan

    @Test
    void captureSpan() {
        Span span = scenario(this::doCaptureSpan);
        assertThat(span)
            .hasName("SpanExitTest#doCaptureSpan")
            .hasType("app")
            .isNotExit();
    }

    @CaptureSpan
    void doCaptureSpan() {

    }

    @Test
    void captureSpanExit1() {
        Span span = scenario(this::doCaptureSpanExit1);
        assertThat(span)
            .hasName("SpanExitTest#doCaptureSpanExit1")
            .hasType("app")
            .isExit();
    }

    @CaptureSpan(asExit = true)
    void doCaptureSpanExit1() {

    }

    @Test
    void captureSpanExit2() {
        Span span = scenario(this::doCaptureSpanExit2);
        assertThat(span)
            .hasName("SpanExitTest#doCaptureSpanExit2")
            .hasType("app")
            .hasSubType("my-database")
            .isExit();
    }

    @CaptureSpan(asExit = true, subtype = "my-database")
    void doCaptureSpanExit2() {

    }

    @Test
    void captureSpanExit3() {
        Span span = scenario(this::doCaptureSpanExit3);
        assertThat(span)
            .hasName("SpanExitTest#doCaptureSpanExit3")
            .hasType("app")
            .hasSubType("my-database")
            .isExit();
    }

    @CaptureSpan(asExit = true, subtype = "my-database")
    void doCaptureSpanExit3() {
        doCaptureSpanExit2();
        // nested span should be ignored within exit span
    }

    // @Traced

    @Test
    void traced() {
        Span span = scenario(this::doTraced);
        assertThat(span)
            .hasName("SpanExitTest#doTraced")
            .hasType("app")
            .isNotExit();
    }

    @Traced
    void doTraced() {
    }

    @Test
    void tracedExit1() {
        Span span = scenario(this::doTracedExit1);
        assertThat(span)
            .hasName("SpanExitTest#doTracedExit1")
            .hasType("app")
            .isExit();
    }

    @Traced(asExit = true)
    void doTracedExit1() {

    }

    @Test
    void tracedExit2() {
        Span span = scenario(this::doTracedExit2);
        assertThat(span)
            .hasName("SpanExitTest#doTracedExit2")
            .hasType("other")
            .hasSubType("another-backend")
            .isExit();
    }

    @Traced(asExit = true, type = "other", subtype = "another-backend")
    void doTracedExit2() {

    }

    @Test
    void tracedExit3() {
        Span span = scenario(this::doTracedExit3);
        assertThat(span)
            .hasName("SpanExitTest#doTracedExit3")
            .hasType("app")
            .hasSubType("my-database")
            .isExit();
    }

    @Traced(asExit = true, subtype = "my-database")
    void doTracedExit3() {
        // nested span should be ignored within exit span
        doTracedExit1();
    }

    // common

    private Span scenario(Runnable task) {
        Transaction transaction = ElasticApm.startTransaction();
        try (Scope activate = transaction.activate()) {
            task.run();
        }
        transaction.end();
        List<Span> spans = reporter.getSpans();
        assertThat(spans)
            .describedAs("only a single span is expected to be created")
            .hasSize(1);
        return spans.get(0);
    }

}
