/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.profiler;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CallTreeSpanifyTest {

    private MockReporter reporter;
    private ElasticApmTracer tracer;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        tracer = MockTracer.createRealTracer(reporter);
    }

    @AfterEach
    void tearDown() {
        reporter.assertRecycledAfterDecrementingReferences();
    }

    @Test
    void testSpanification() {
        CallTree.Root callTree = CallTreeTest.getCallTree(tracer, new String[]{
            " d  ",
            " c  ",
            " bb ",
            "aaae"
        });
        long rootTimestamp = callTree.getTimestampUs();
        callTree.spanify();
        assertThat(reporter.getSpans()).hasSize(4);
        assertThat(reporter.getSpans().stream()
            .map(Span::getNameAsString)
        ).containsExactly("d", "b", "a", "e");

        Span d = reporter.getSpans().get(0);
        assertThat(d.getNameAsString()).isEqualTo("d");
        assertThat(d.getTimestamp()).isEqualTo(rootTimestamp + TimeUnit.MILLISECONDS.toMicros(10));
        assertThat(d.getDuration()).isEqualTo(TimeUnit.MILLISECONDS.toMicros(0));
        assertThat(d.getStackFrames()).containsExactly("c");

        Span b = reporter.getSpans().get(1);
        assertThat(b.getNameAsString()).isEqualTo("b");
        assertThat(b.getTimestamp()).isEqualTo(rootTimestamp + TimeUnit.MILLISECONDS.toMicros(10));
        assertThat(b.getDuration()).isEqualTo(TimeUnit.MILLISECONDS.toMicros(10));
        assertThat(b.getStackFrames()).isEmpty();

        Span a = reporter.getSpans().get(2);
        assertThat(a.getNameAsString()).isEqualTo("a");
        assertThat(a.getTimestamp()).isEqualTo(rootTimestamp);
        assertThat(a.getDuration()).isEqualTo(TimeUnit.MILLISECONDS.toMicros(20));
        assertThat(a.getStackFrames()).isEmpty();

        Span e = reporter.getSpans().get(3);
        assertThat(e.getNameAsString()).isEqualTo("e");
        assertThat(e.getTimestamp()).isEqualTo(rootTimestamp + TimeUnit.MILLISECONDS.toMicros(30));
        assertThat(e.getDuration()).isEqualTo(TimeUnit.MILLISECONDS.toMicros(0));
        assertThat(e.getStackFrames()).isEmpty();
    }

    @Test
    void testCallTreeWithActiveSpan() {
        TraceContext rootContext = CallTreeTest.rootTraceContext(tracer);
        CallTree.Root root = CallTree.createRoot(rootContext.getTraceContext().copy(), 0);
        root.addStackTrace(List.of("a"), 0);

        TraceContext spanContext = TraceContext.with64BitId(tracer);
        TraceContext.fromParent().asChildOf(spanContext, rootContext);

        root.setActiveSpan(spanContext);
        root.addStackTrace(List.of("b", "a"), TimeUnit.MILLISECONDS.toNanos(10));
        root.setActiveSpan(rootContext);

        root.addStackTrace(List.of("a"), TimeUnit.MILLISECONDS.toNanos(20));
        root.end();

        System.out.println(root);

        assertThat(root.getCount()).isEqualTo(3);
        assertThat(root.getDurationUs()).isEqualTo(20_000);
        assertThat(root.getChildren()).hasSize(1);

        CallTree a = root.getLastChild();
        assertThat(a).isNotNull();
        assertThat(a.getFrame()).isEqualTo("a");
        assertThat(a.getCount()).isEqualTo(3);
        assertThat(a.getDurationUs()).isEqualTo(20_000);
        assertThat(a.getChildren()).hasSize(1);

        CallTree b = a.getLastChild();
        assertThat(b).isNotNull();
        assertThat(b.getFrame()).isEqualTo("b");
        assertThat(b.getCount()).isEqualTo(1);
        assertThat(b.getDurationUs()).isEqualTo(0);
        assertThat(b.getChildren()).isEmpty();

        root.spanify();

        assertThat(reporter.getSpans()).hasSize(2);
        assertThat(reporter.getSpans().get(1).getTraceContext().isChildOf(spanContext));
        assertThat(reporter.getSpans().get(0).getTraceContext().isChildOf(rootContext));
    }

}
