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
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.tracer.configuration.TimeDuration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@SuppressWarnings("unused")
public class SpanDiscardingTest extends AbstractApiTest {

    @Test
    void testDiscarding() {
        doTest(Scenario.DISCARD);
    }

    @Test
    void testDiscarding_capturedAnnotation() {
        doTest(Scenario.DISCARD_CAPTURE_ANNOTATION);
    }

    @Test
    void testDiscarding_tracedAnnotation() {
        doTest(Scenario.DISCARD_TRACED_ANNOTATION);
    }

    @Test
    void testNonDiscarding() {
        doTest(Scenario.NON_DISCARD);
    }

    @Test
    void testNonDiscarding_captureAnnotation() {
        doTest(Scenario.NON_DISCARD_CAPTURE_ANNOTATION);
    }

    @Test
    void testNonDiscarding_tracedAnnotation() {
        doTest(Scenario.NON_DISCARD_TRACED_ANNOTATION);
    }

    private void doTest(Scenario scenario) {
        doReturn(TimeDuration.of("100ms")).when(config.getConfig(CoreConfiguration.class)).getSpanMinDuration();
        Transaction transaction = ElasticApm.startTransaction();
        try (Scope activate = transaction.activate()) {
            parentSpan(scenario);
        }
        List<co.elastic.apm.agent.impl.transaction.Span> spans = reporter.getSpans();
        switch (scenario) {
            case DISCARD:
            case DISCARD_TRACED_ANNOTATION:
            case DISCARD_CAPTURE_ANNOTATION:
                assertThat(spans).isEmpty();
                break;
            case NON_DISCARD:
            case NON_DISCARD_TRACED_ANNOTATION:
            case NON_DISCARD_CAPTURE_ANNOTATION:
                assertThat(spans).hasSize(2);
                assertThat(spans).anyMatch(span -> span.getNameAsString().equals("parent"));
                assertThat(spans).anyMatch(span -> span.getNameAsString().equals("not-discarded"));
        }
        transaction.end();
    }

    private void parentSpan(Scenario scenario) {
        Span span = ElasticApm.currentSpan().startSpan().setName("parent");
        try (Scope activate = span.activate()) {
            switch (scenario) {
                case DISCARD:
                    discarded();
                    break;
                case DISCARD_CAPTURE_ANNOTATION:
                    discarded_captureAnnotation();
                    break;
                case DISCARD_TRACED_ANNOTATION:
                    discarded_tracedAnnotation();
                    break;
                case NON_DISCARD:
                    notDiscarded();
                    break;
                case NON_DISCARD_CAPTURE_ANNOTATION:
                    notDiscarded_captureAnnotation();
                    break;
                case NON_DISCARD_TRACED_ANNOTATION:
                    notDiscarded_tracedAnnotation();
                    break;
            }
        }
        span.end();
    }

    private void discarded() {
        Span span = ElasticApm.currentSpan().startSpan().setName("discarded");
        try (Scope activate = span.activate()) {
            childSpan();
        }
        span.end();
    }

    @CaptureSpan("discarded")
    private void discarded_captureAnnotation() {
        childSpan();
    }

    @Traced("discarded")
    private void discarded_tracedAnnotation() {
        childSpan();
    }

    private void notDiscarded() {
        Span span = ElasticApm.currentSpan().startSpan().setName("not-discarded").setNonDiscardable();
        try (Scope activate = span.activate()) {
            childSpan();
        }
        span.end();
    }

    @CaptureSpan(value = "not-discarded", discardable = false)
    private void notDiscarded_captureAnnotation() {
        childSpan();
    }

    @Traced(value = "not-discarded", discardable = false)
    private void notDiscarded_tracedAnnotation() {
        childSpan();
    }

    private void childSpan() {
        ElasticApm.currentSpan().startSpan().setName("child").end();
    }

    private enum Scenario {
        DISCARD,
        DISCARD_CAPTURE_ANNOTATION,
        DISCARD_TRACED_ANNOTATION,
        NON_DISCARD,
        NON_DISCARD_TRACED_ANNOTATION,
        NON_DISCARD_CAPTURE_ANNOTATION
    }
}
