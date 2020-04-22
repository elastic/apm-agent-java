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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class DiscardSpanTest {
    private ElasticApmTracer tracer;
    private MockReporter reporter;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        tracer = MockTracer.createRealTracer(reporter);
    }

    @AfterEach
    void cleanupAndCheck() {
        reporter.assertRecycledAfterDecrementingReferences();
        tracer.stop();
    }

    @Test
    void testContextPropagatingSpansAreNonDiscardable() {
        Transaction transaction = tracer.startRootTransaction(null);
        assertThat(transaction).isNotNull();
        try {
            Span span = transaction.createSpan();
            try {
                span.setOutgoingTraceContextHeaders(new HashMap<>(), (k, v, map) -> map.put(k, v));
                assertThat(span.isDiscardable()).isFalse();
            } finally {
                span.end();
            }
        } finally {
            transaction.end();
        }
    }

    @Test
    void testErrorCapturingMakesSpansNonDiscardable() {
        when(tracer.getConfigurationRegistry().getConfig(CoreConfiguration.class).getTransactionMaxSpans()).thenReturn(2);
        Transaction transaction = tracer.startRootTransaction(null);
        assertThat(transaction).isNotNull();
        try {
            Span span = transaction.createSpan();
            try {
                span.requestDiscarding().appendToName("non-discardable").captureException(new Exception());
                assertThat(span.isDiscardable()).isFalse();
            } finally {
                span.end();
            }
        } finally {
            transaction.end();
        }
        assertThat(reporter.getSpans().stream().map(Span::getNameAsString)).containsExactly("non-discardable");
    }

    @Test
    void testParentsOfContextPropagatingSpansAreNonDiscardable() {
        Transaction transaction = tracer.startRootTransaction(null);
        assertThat(transaction).isNotNull();
        try {
            Span parentSpan = transaction.createSpan();
            try {
                Span contextPropagatingSpan = parentSpan.createSpan();
                try {
                    contextPropagatingSpan.setOutgoingTraceContextHeaders(new HashMap<>(), TextHeaderMapAccessor.INSTANCE);
                    assertThat(contextPropagatingSpan.isDiscardable()).isFalse();
                    assertThat(parentSpan.isDiscardable()).isFalse();
                } finally {
                    contextPropagatingSpan.end();
                }
            } finally {
                parentSpan.end();
            }
        } finally {
            transaction.end();
        }
    }

    @Test
    void testDiscardSpanLimit() {
        when(tracer.getConfigurationRegistry().getConfig(CoreConfiguration.class).getTransactionMaxSpans()).thenReturn(2);
        Transaction transaction = tracer.startRootTransaction(null);
        assertThat(transaction).isNotNull();
        try {
            transaction.createSpan().appendToName("1st").end();
            transaction.createSpan().appendToName("discarded").requestDiscarding().end();
            transaction.createSpan().appendToName("2nd").end();
            transaction.createSpan().appendToName("exceeds limit").end();
        } finally {
            transaction.end();
        }
        assertThat(reporter.getSpans().stream().map(Span::getNameAsString)).containsExactly("1st", "2nd");
    }

    @Test
    void testDiscardSpanLimitNesting() {
        when(tracer.getConfigurationRegistry().getConfig(CoreConfiguration.class).getTransactionMaxSpans()).thenReturn(2);
        Transaction transaction = tracer.startRootTransaction(null);
        assertThat(transaction).isNotNull();
        try {
            Span first = transaction.createSpan().appendToName("1st");
            try {
                Span second = first.createSpan().appendToName("2nd");
                try {
                    second.createSpan().appendToName("exceeds limit").end();
                } finally {
                    second.end();
                }
            } finally {
                first.end();
            }
        } finally {
            transaction.end();
        }
        assertThat(reporter.getSpans().stream().map(Span::getNameAsString)).containsExactly("2nd", "1st");
    }

    @Test
    void testDiscardSpanLimitNesting2() {
        when(tracer.getConfigurationRegistry().getConfig(CoreConfiguration.class).getTransactionMaxSpans()).thenReturn(2);
        Transaction transaction = tracer.startRootTransaction(null);
        assertThat(transaction).isNotNull();
        try {
            Span first = transaction.createSpan().appendToName("1st");
            try {
                Span second = first.createSpan().requestDiscarding().appendToName("discarded 1");
                try {
                    Span exceedsLimit1 = second.createSpan().requestDiscarding().appendToName("exceeds limit 1");
                    try {
                        assertThat(exceedsLimit1.isSampled()).isFalse();
                    } finally {
                        exceedsLimit1.end();
                    }
                } finally {
                    second.end();
                }
            } finally {
                first.end();
            }
            // will not be dropped due to limit
            transaction.createSpan().appendToName("2nd").end();
            transaction.createSpan().appendToName("exceeds limit 2").end();
        } finally {
            transaction.end();
        }
        assertThat(reporter.getSpans().stream().map(Span::getNameAsString)).containsExactly("1st", "2nd");
    }
}
