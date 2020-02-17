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
import co.elastic.apm.agent.impl.transaction.StackFrame;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.objectpool.NoopObjectPool;
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
        tracer.stop();
    }

    @Test
    void testSpanification() throws Exception {
        CallTree.Root callTree = CallTreeTest.getCallTree(tracer, new String[]{
            " d  ",
            " c  ",
            " bb ",
            "aaae"
        });
        callTree.spanify();
        assertThat(reporter.getSpans()).hasSize(4);
        assertThat(reporter.getSpans().stream()
            .map(Span::getNameAsString)
        ).containsExactly("CallTreeTest#d", "CallTreeTest#b", "CallTreeTest#a", "CallTreeTest#e");

        Span d = reporter.getSpans().get(0);
        assertThat(d.getNameAsString()).isEqualTo("CallTreeTest#d");
        assertThat(d.getDuration()).isEqualTo(TimeUnit.MILLISECONDS.toMicros(0));
        assertThat(d.getStackFrames().stream().map(StackFrame::getMethodName)).containsExactly("c");

        Span b = reporter.getSpans().get(1);
        assertThat(b.getNameAsString()).isEqualTo("CallTreeTest#b");
        assertThat(b.getDuration()).isEqualTo(TimeUnit.MILLISECONDS.toMicros(10));
        assertThat(b.getStackFrames()).isEmpty();

        Span a = reporter.getSpans().get(2);
        assertThat(a.getNameAsString()).isEqualTo("CallTreeTest#a");
        assertThat(a.getDuration()).isEqualTo(TimeUnit.MILLISECONDS.toMicros(20));
        assertThat(a.getStackFrames()).isEmpty();

        Span e = reporter.getSpans().get(3);
        assertThat(e.getNameAsString()).isEqualTo("CallTreeTest#e");
        assertThat(e.getDuration()).isEqualTo(TimeUnit.MILLISECONDS.toMicros(0));
        assertThat(e.getStackFrames()).isEmpty();
    }

    @Test
    void testCallTreeWithActiveSpan() {
        TraceContext rootContext = CallTreeTest.rootTraceContext(tracer);
        TraceContext traceContext = rootContext.getTraceContext();
        CallTree.Root root = CallTree.createRoot(NoopObjectPool.ofRecyclable(() -> new CallTree.Root(tracer)), traceContext.serialize(), traceContext.getServiceName(), 0);
        NoopObjectPool<CallTree> callTreePool = NoopObjectPool.ofRecyclable(CallTree::new);
        root.addStackTrace(tracer, List.of(StackFrame.of("A", "a")), 0, callTreePool);

        TraceContext spanContext = TraceContext.with64BitId(tracer);
        TraceContext.fromParent().asChildOf(spanContext, rootContext);

        root.onActivation(spanContext.serialize(), TimeUnit.MILLISECONDS.toNanos(5));
        root.addStackTrace(tracer, List.of(StackFrame.of("A", "b"), StackFrame.of("A", "a")), TimeUnit.MILLISECONDS.toNanos(10), callTreePool);
        root.onDeactivation(rootContext.serialize(), TimeUnit.MILLISECONDS.toNanos(15));

        root.addStackTrace(tracer, List.of(StackFrame.of("A", "a")), TimeUnit.MILLISECONDS.toNanos(20), callTreePool);
        root.end();

        System.out.println(root);

        assertThat(root.getCount()).isEqualTo(3);
        assertThat(root.getDurationUs()).isEqualTo(20_000);
        assertThat(root.getChildren()).hasSize(1);

        CallTree a = root.getLastChild();
        assertThat(a).isNotNull();
        assertThat(a.getFrame().getMethodName()).isEqualTo("a");
        assertThat(a.getCount()).isEqualTo(3);
        assertThat(a.getDurationUs()).isEqualTo(20_000);
        assertThat(a.getChildren()).hasSize(1);

        CallTree b = a.getLastChild();
        assertThat(b).isNotNull();
        assertThat(b.getFrame().getMethodName()).isEqualTo("b");
        assertThat(b.getCount()).isEqualTo(1);
        assertThat(b.getDurationUs()).isEqualTo(0);
        assertThat(b.getChildren()).isEmpty();

        root.spanify();

        assertThat(reporter.getSpans()).hasSize(2);
        assertThat(reporter.getSpans().get(1).getTraceContext().isChildOf(spanContext));
        assertThat(reporter.getSpans().get(0).getTraceContext().isChildOf(rootContext));
    }

}
