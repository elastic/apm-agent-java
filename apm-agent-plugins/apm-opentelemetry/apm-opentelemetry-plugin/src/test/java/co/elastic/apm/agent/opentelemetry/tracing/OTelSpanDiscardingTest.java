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
package co.elastic.apm.agent.opentelemetry.tracing;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static co.elastic.apm.agent.opentelemetry.tracing.BehavioralAttributes.DISCARDABLE;
import static co.elastic.apm.agent.opentelemetry.tracing.OTelSpan.ILLEGAL_ATTRIBUTE_VALUE_TYPE_MESSAGE_FORMAT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

@SuppressWarnings("unused")
public class OTelSpanDiscardingTest extends AbstractOpenTelemetryTest {

    public static final String NOT_DISCARDED_SPAN_NAME = "not-discarded";

    @Before
    public void before() {
        doReturn(TimeDuration.of("100ms")).when(config.getConfig(CoreConfiguration.class)).getSpanMinDuration();
    }

    @Test
    public void testWrongValueType() {
        final Span transaction = otelTracer.spanBuilder("transaction").startSpan();
        String attributeValue = "String value";
        assertThatThrownBy(() -> transaction.setAttribute(DISCARDABLE, attributeValue))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(String.format(ILLEGAL_ATTRIBUTE_VALUE_TYPE_MESSAGE_FORMAT, DISCARDABLE, attributeValue));
    }

    @Test
    public void testDiscarding() {
        runTest(Scenario.DISCARD);
    }

    @Test
    public void testDiscarding_setExplicitly() {
        runTest(Scenario.DISCARD_SET_EXPLICITLY);
    }

    @Test
    public void testNotDiscarding_Span_keyAsAttribute() {
        runTest(Scenario.NON_DISCARD_SPAN_AS_ATTRIBUTE);
    }

    @Test
    public void testNotDiscarding_Span_keyAsString() {
        runTest(Scenario.NON_DISCARD_SPAN_AS_STRING);
    }

    @Test
    public void testNotDiscarding_Builder_keyAsAttribute() {
        runTest(Scenario.NON_DISCARD_BUILDER_AS_ATTRIBUTE);
    }

    @Test
    public void testNotDiscarding_Builder_keyAsString() {
        runTest(Scenario.NON_DISCARD_BUILDER_AS_STRING);
    }

    private void runTest(Scenario scenario) {
        Span transaction = otelTracer.spanBuilder("transaction").startSpan();
        try (Scope scope = transaction.makeCurrent()) {
            parentSpan(scenario);
        }
        transaction.end();
        assertThat(reporter.getTransactions()).hasSize(1);
        List<co.elastic.apm.agent.impl.transaction.Span> spans = reporter.getSpans();
        switch (scenario) {
            case DISCARD:
            case DISCARD_SET_EXPLICITLY:
                assertThat(spans).isEmpty();
                break;
            case NON_DISCARD_SPAN_AS_STRING:
            case NON_DISCARD_BUILDER_AS_STRING:
            case NON_DISCARD_SPAN_AS_ATTRIBUTE:
            case NON_DISCARD_BUILDER_AS_ATTRIBUTE:
                assertThat(spans).hasSize(2);
                assertThat(spans).anyMatch(span -> span.getNameAsString().equals("parent"));
                assertThat(spans).anyMatch(span -> span.getNameAsString().equals(NOT_DISCARDED_SPAN_NAME));
        }
    }

    private void parentSpan(Scenario scenario) {
        Span parent = otelTracer.spanBuilder("parent").startSpan();
        try (Scope scope = parent.makeCurrent()) {
            switch (scenario) {
                case DISCARD:
                    discarded();
                    break;
                case DISCARD_SET_EXPLICITLY:
                    discarded_setExplicitly();
                    break;
                case NON_DISCARD_SPAN_AS_ATTRIBUTE:
                    notDiscarded_Span_asAttribute();
                    break;
                case NON_DISCARD_SPAN_AS_STRING:
                    notDiscarded_Span_asString();
                    break;
                case NON_DISCARD_BUILDER_AS_ATTRIBUTE:
                    notDiscarded_Builder_asAttribute();
                    break;
                case NON_DISCARD_BUILDER_AS_STRING:
                    notDiscarded_Builder_asString();
                    break;
            }
        }
        parent.end();
    }

    private void discarded() {
        Span span = otelTracer.spanBuilder("discarded").startSpan();
        try (Scope activate = span.makeCurrent()) {
            childSpan();
        }
        span.end();
    }

    private void discarded_setExplicitly() {
        Span span = otelTracer.spanBuilder("discarded").startSpan().setAttribute(AttributeKey.booleanKey(DISCARDABLE), true);
        try (Scope activate = span.makeCurrent()) {
            childSpan();
        }
        span.end();
    }

    private void notDiscarded_Span_asAttribute() {
        Span span = otelTracer.spanBuilder(NOT_DISCARDED_SPAN_NAME).startSpan().setAttribute(AttributeKey.booleanKey(DISCARDABLE), false);
        try (Scope activate = span.makeCurrent()) {
            childSpan();
        }
        span.end();
    }

    private void notDiscarded_Span_asString() {
        Span span = otelTracer.spanBuilder(NOT_DISCARDED_SPAN_NAME).startSpan().setAttribute(DISCARDABLE, false);
        try (Scope activate = span.makeCurrent()) {
            childSpan();
        }
        span.end();
    }

    private void notDiscarded_Builder_asAttribute() {
        Span span = otelTracer.spanBuilder(NOT_DISCARDED_SPAN_NAME).setAttribute(AttributeKey.booleanKey(DISCARDABLE), false).startSpan();
        try (Scope activate = span.makeCurrent()) {
            childSpan();
        }
        span.end();
    }

    private void notDiscarded_Builder_asString() {
        Span span = otelTracer.spanBuilder(NOT_DISCARDED_SPAN_NAME).setAttribute(DISCARDABLE, false).startSpan();
        try (Scope activate = span.makeCurrent()) {
            childSpan();
        }
        span.end();
    }

    private void childSpan() {
        otelTracer.spanBuilder("child").startSpan().end();
    }

    private enum Scenario {
        DISCARD,
        DISCARD_SET_EXPLICITLY,
        NON_DISCARD_BUILDER_AS_ATTRIBUTE,
        NON_DISCARD_BUILDER_AS_STRING,
        NON_DISCARD_SPAN_AS_ATTRIBUTE,
        NON_DISCARD_SPAN_AS_STRING
    }
}
