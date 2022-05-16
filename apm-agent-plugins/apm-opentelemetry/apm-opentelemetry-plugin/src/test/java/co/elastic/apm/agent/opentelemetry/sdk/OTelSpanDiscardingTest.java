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
package co.elastic.apm.agent.opentelemetry.sdk;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@SuppressWarnings("unused")
public class OTelSpanDiscardingTest extends AbstractOpenTelemetryTest {

    @Before
    public void before() {
        doReturn(TimeDuration.of("100ms")).when(config.getConfig(CoreConfiguration.class)).getSpanMinDuration();
    }

    @Test
    public void testDiscarding() {
        runTest(Scenario.DISCARD);
    }

    @Test
    public void testNotDiscarding_Span() {
        runTest(Scenario.NON_DISCARD_SPAN);
    }

    @Test
    public void testNotDiscarding_Builder() {
        runTest(Scenario.NON_DISCARD_BUILDER);
    }

    private void runTest(Scenario scenario) {
        Span transaction = otelTracer.spanBuilder("transaction").startSpan();
        try (Scope scope = transaction.makeCurrent()) {
            parent(scenario);
        }
        transaction.end();
        assertThat(reporter.getTransactions()).hasSize(1);
        List<co.elastic.apm.agent.impl.transaction.Span> spans = reporter.getSpans();
        switch (scenario) {
            case DISCARD:
                assertThat(spans).isEmpty();
                break;
            case NON_DISCARD_SPAN:
            case NON_DISCARD_BUILDER:
                assertThat(spans).hasSize(2);
                assertThat(spans).anyMatch(span -> span.getNameAsString().equals("parent"));
                assertThat(spans).anyMatch(span -> span.getNameAsString().equals("not-discarded"));
        }
    }

    private void parent(Scenario scenario) {
        Span parent = otelTracer.spanBuilder("parent").startSpan();
        try (Scope scope = parent.makeCurrent()) {
            switch (scenario) {
                case DISCARD:
                    discarded();
                    break;
                case NON_DISCARD_SPAN:
                    notDiscarded_Span();
                    break;
                case NON_DISCARD_BUILDER:
                    notDiscarded_Builder();
                    break;
            }
        }
        parent.end();
    }

    private void discarded() {
        Span span = otelTracer.spanBuilder("discarded").startSpan();
        try (Scope activate = span.makeCurrent()) {
            child();
        }
        span.end();
    }

    private void notDiscarded_Span() {
        Span span = otelTracer.spanBuilder("not-discarded").startSpan().setAttribute(BehavioralAttributes.NON_DISCARDABLE, true);
        try (Scope activate = span.makeCurrent()) {
            child();
        }
        span.end();
    }

    private void notDiscarded_Builder() {
        Span span = otelTracer.spanBuilder("not-discarded").setAttribute(BehavioralAttributes.NON_DISCARDABLE, true).startSpan();
        try (Scope activate = span.makeCurrent()) {
            child();
        }
        span.end();
    }

    private void child() {
        otelTracer.spanBuilder("child").startSpan().end();
    }

    private enum Scenario {
        DISCARD,
        NON_DISCARD_BUILDER,
        NON_DISCARD_SPAN
    }
}
