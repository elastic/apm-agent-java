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
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@SuppressWarnings("unused")
public class SpanDiscardingTest extends AbstractApiTest {

    @Test
    void testSpanDiscarding() {
        doReturn(TimeDuration.of("100ms")).when(config.getConfig(CoreConfiguration.class)).getSpanMinDuration();
        Transaction transaction = ElasticApm.startTransaction();
        try (Scope activate = transaction.activate()) {
            rootMethod(true);
            rootMethod(false);
        }
        List<co.elastic.apm.agent.impl.transaction.Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(2);
        assertThat(spans).anyMatch(span -> span.getNameAsString().equals("root-false"));
        assertThat(spans).anyMatch(span -> span.getNameAsString().equals("not-discarded"));
        transaction.end();
    }

    private void rootMethod(boolean discardChild) {
        Span span = ElasticApm.currentSpan().startSpan().setName("root-" + discardChild);
        try (Scope activate = span.activate()) {
            if (discardChild) {
                discarded();
            } else {
                notDiscarded();
            }
        }
        span.end();
    }

    private void discarded() {
        Span span = ElasticApm.currentSpan().startSpan().setName("discarded");
        try (Scope activate = span.activate()) {
            child();
        }
        span.end();
    }

    private void notDiscarded() {
        Span span = ElasticApm.currentSpan().startSpan().setName("not-discarded").setNonDiscardable();
        try (Scope activate = span.activate()) {
            child();
        }
        span.end();
    }

    private void child() {
        ElasticApm.currentSpan().startSpan().setName("child").end();
    }
}
