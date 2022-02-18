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

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ExactMatchCompressionStrategyTest extends AbstractCompressionStrategyTest {

    ExactMatchCompressionStrategyTest() {
        super("exact_match");
    }

    @BeforeAll
    static void setMaxDuration() {
        when(tracer.getConfig(CoreConfiguration.class).getSpanCompressionSameKindMaxDuration()).thenReturn(TimeDuration.of("0ms"));
    }

    @Test
    void testDifferentNameStopsRegularCompression() {
        runInTransactionScope(t -> {
            startExitSpan(t).end();
            startExitSpan(t).withName("Another Name").end();
        });

        List<Span> reportedSpans = reporter.getSpans();
        assertThat(reportedSpans).hasSize(2);
        assertThat(reportedSpans.get(0).isComposite()).isFalse();
        assertThat(reportedSpans.get(1).isComposite()).isFalse();
    }

    @Test
    void testDifferentNameStopsCompositeCompression() {
        runInTransactionScope(t -> {
            startExitSpan(t).end();
            startExitSpan(t).end();
            startExitSpan(t).withName("Another Name").end();
        });

        List<Span> reportedSpans = reporter.getSpans();
        assertThat(reportedSpans).hasSize(2);
        assertCompositeSpan(reportedSpans.get(0), 2);
        assertThat(reportedSpans.get(1).isComposite()).isFalse();
    }

    @Override
    protected String getSpanName() {
        return "Some Name";
    }

    @Override
    protected String getCompositeSpanName(Span span) {
        return getSpanName();
    }
}
