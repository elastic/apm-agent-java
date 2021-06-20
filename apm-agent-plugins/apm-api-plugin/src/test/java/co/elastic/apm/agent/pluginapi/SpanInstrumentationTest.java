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
package co.elastic.apm.agent.pluginapi;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.TextHeaderMapAccessor;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Scope;
import co.elastic.apm.api.Span;
import co.elastic.apm.api.Transaction;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpanInstrumentationTest extends AbstractInstrumentationTest {

    private static final SecureRandom random = new SecureRandom();

    private Transaction transaction;

    @BeforeEach
    void setUp() {
        transaction = ElasticApm.startTransaction();
    }

    @AfterEach
    void tearDown() {
        transaction.end();
    }

    @Test
    void testSetName() {
        Span span = transaction.startSpan();
        span.setName("foo");
        endSpan(span);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("foo");
    }

    @Test
    @SuppressWarnings("deprecation")
    void testLegacyAPIs() {
        reporter.disableCheckStrictSpanType();

        Span span = transaction.createSpan();
        span.setType("foo.bar.baz");
        endSpan(span);
        co.elastic.apm.agent.impl.transaction.Span internalSpan = reporter.getFirstSpan();
        assertThat(internalSpan.getType()).isEqualTo("foo");
        assertThat(internalSpan.getSubtype()).isEqualTo("bar");
        assertThat(internalSpan.getAction()).isEqualTo("baz");
    }

    @Test
    void testTypes() {
        reporter.disableCheckStrictSpanType();

        Span span = transaction.startSpan("foo", "bar", "baz");
        endSpan(span);
        co.elastic.apm.agent.impl.transaction.Span internalSpan = reporter.getFirstSpan();
        assertThat(internalSpan.getType()).isEqualTo("foo");
        assertThat(internalSpan.getSubtype()).isEqualTo("bar");
        assertThat(internalSpan.getAction()).isEqualTo("baz");
    }

    @Test
    void testChaining() {
        reporter.disableCheckStrictSpanType();

        int randomInt = random.nextInt();
        boolean randomBoolean = random.nextBoolean();
        String randomString = RandomStringUtils.randomAlphanumeric(3);
        Span span = transaction.startSpan("foo", null, null)
            .setName("foo")
            .addLabel("foo", "bar")
            .setLabel("stringKey", randomString)
            .setLabel("numberKey", randomInt)
            .setLabel("booleanKey", randomBoolean)
            .setDestinationAddress("localhost", 443)
            .setDestinationService("resource:123");
        endSpan(span);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("foo");
        assertThat(reporter.getFirstSpan().getType()).isEqualTo("foo");
        assertThat(reporter.getFirstSpan().getContext().getLabel("foo")).isEqualTo("bar");
        assertThat(reporter.getFirstSpan().getContext().getLabel("stringKey")).isEqualTo(randomString);
        assertThat(reporter.getFirstSpan().getContext().getLabel("numberKey")).isEqualTo(randomInt);
        assertThat(reporter.getFirstSpan().getContext().getLabel("booleanKey")).isEqualTo(randomBoolean);
        assertThat(reporter.getFirstSpan().getContext().getDestination().getAddress().toString()).isEqualTo("localhost");
        assertThat(reporter.getFirstSpan().getContext().getDestination().getPort()).isEqualTo(443);
        assertThat(reporter.getFirstSpan().getContext().getDestination().getService().getResource().toString()).isEqualTo("resource:123");
    }

    private void endSpan(Span span) {
        span.end();
        transaction.end();
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getTransactions()).hasSize(1);
    }

    @Test
    void testScope() {
        Span span = transaction.startSpan();
        assertThat(ElasticApm.currentSpan().getId()).isNotEqualTo(span.getId());
        try (final Scope scope = span.activate()) {
            assertThat(ElasticApm.currentSpan().getId()).isEqualTo(span.getId());
            ElasticApm.currentSpan().startSpan().end();
        }
        span.end();
        transaction.end();
        assertThat(reporter.getSpans()).hasSize(2);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans().get(0).isChildOf(reporter.getSpans().get(1))).isTrue();
        assertThat(reporter.getSpans().get(1).isChildOf(reporter.getFirstTransaction())).isTrue();
    }

    @Test
    void testSampled() {
        assertThat(ElasticApm.currentSpan().isSampled()).isFalse();
        assertThat(ElasticApm.currentTransaction().isSampled()).isFalse();
        final Transaction transaction = ElasticApm.startTransaction();
        assertThat(transaction.isSampled()).isTrue();
        Span span = transaction.startSpan();
        assertThat(span.isSampled()).isTrue();
        span.end();
        transaction.end();
    }

    @Test
    void testTraceHeadersNoop() {
        assertContainsNoTracingHeaders(ElasticApm.currentSpan());
        assertContainsNoTracingHeaders(ElasticApm.currentTransaction());
    }

    @Test
    void testTraceHeaders() {
        Span span = transaction.startSpan();
        assertContainsTracingHeaders(span);
        assertContainsTracingHeaders(transaction);
        span.end();
    }

    private void assertContainsNoTracingHeaders(Span span) {
        try (Scope scope = span.activate()) {
            final Map<String, String> tracingHeaders = new HashMap<>();
            span.injectTraceHeaders(tracingHeaders::put);
            span.injectTraceHeaders(null);
            assertThat(tracingHeaders).isEmpty();
        }
    }

    private void assertContainsTracingHeaders(Span span) {
        try (Scope scope = span.activate()) {
            final Map<String, String> tracingHeaders = new HashMap<>();
            span.injectTraceHeaders(tracingHeaders::put);
            span.injectTraceHeaders(null);
            assertThat(TraceContext.containsTraceContextTextHeaders(tracingHeaders, TextHeaderMapAccessor.INSTANCE)).isTrue();
        }
    }
}
