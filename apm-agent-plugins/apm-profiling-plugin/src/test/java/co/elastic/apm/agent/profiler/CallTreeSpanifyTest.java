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
package co.elastic.apm.agent.profiler;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.StackFrame;
import co.elastic.apm.agent.impl.transaction.TraceContextImpl;
import co.elastic.apm.agent.objectpool.NoopObjectPool;
import co.elastic.apm.agent.testutils.DisabledOnAppleSilicon;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.condition.OS;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

class CallTreeSpanifyTest {

    private MockReporter reporter;
    private ElasticApmTracer tracer;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        // disable scheduled profiling to not interfere with this test
        doReturn(false).when(config.getConfig(ProfilingConfiguration.class)).isProfilingEnabled();
        tracer = MockTracer.createRealTracer(reporter, config, false);
    }

    @AfterEach
    void tearDown() throws IOException {
        Objects.requireNonNull(tracer.getLifecycleListener(ProfilingFactory.class)).getProfiler().clear();
        reporter.assertRecycledAfterDecrementingReferences();
        tracer.stop();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisabledOnAppleSilicon
    @DisabledForJreRange(min = JRE.JAVA_24)
    void testSpanification() throws Exception {
        CallTree.Root callTree = CallTreeTest.getCallTree(tracer, new String[]{
            " dd   ",
            " cc   ",
            " bbb  ",
            "aaaaee"
        });
        assertThat(callTree.spanify()).isEqualTo(4);
        assertThat(reporter.getSpans()).hasSize(4);
        assertThat(reporter.getSpans().stream()
            .map(SpanImpl::getNameAsString)
        ).containsExactly("CallTreeTest#d", "CallTreeTest#b", "CallTreeTest#a", "CallTreeTest#e");

        SpanImpl d = reporter.getSpans().get(0);
        assertThat(d.getNameAsString()).isEqualTo("CallTreeTest#d");
        assertThat(d.getDuration()).isEqualTo(TimeUnit.MILLISECONDS.toMicros(10));
        assertThat(d.getStackFrames().stream().map(StackFrame::getMethodName)).containsExactly("c");

        SpanImpl b = reporter.getSpans().get(1);
        assertThat(b.getNameAsString()).isEqualTo("CallTreeTest#b");
        assertThat(b.getDuration()).isEqualTo(TimeUnit.MILLISECONDS.toMicros(20));
        assertThat(b.getStackFrames()).isEmpty();

        SpanImpl a = reporter.getSpans().get(2);
        assertThat(a.getNameAsString()).isEqualTo("CallTreeTest#a");
        assertThat(a.getDuration()).isEqualTo(TimeUnit.MILLISECONDS.toMicros(30));
        assertThat(a.getStackFrames()).isEmpty();

        SpanImpl e = reporter.getSpans().get(3);
        assertThat(e.getNameAsString()).isEqualTo("CallTreeTest#e");
        assertThat(e.getDuration()).isEqualTo(TimeUnit.MILLISECONDS.toMicros(10));
        assertThat(e.getStackFrames()).isEmpty();
    }

    @Test
    void testCallTreeWithActiveSpan() {
        TraceContextImpl rootContext = CallTreeTest.rootTraceContext(tracer);
        CallTree.Root root = CallTree.createRoot(NoopObjectPool.ofRecyclable(() -> new CallTree.Root(tracer)), rootContext.serialize(), rootContext.getServiceName(), rootContext.getServiceVersion(),0);
        NoopObjectPool<CallTree> callTreePool = NoopObjectPool.ofRecyclable(CallTree::new);
        root.addStackTrace(tracer, List.of(StackFrame.of("A", "a")), 0, callTreePool, 0);

        TraceContextImpl spanContext = TraceContextImpl.with64BitId(tracer);
        TraceContextImpl.fromParentContext().asChildOf(spanContext, rootContext);

        root.onActivation(spanContext.serialize(), TimeUnit.MILLISECONDS.toNanos(5));
        root.addStackTrace(tracer, List.of(StackFrame.of("A", "b"), StackFrame.of("A", "a")), TimeUnit.MILLISECONDS.toNanos(10), callTreePool, 0);
        root.addStackTrace(tracer, List.of(StackFrame.of("A", "b"), StackFrame.of("A", "a")), TimeUnit.MILLISECONDS.toNanos(20), callTreePool, 0);
        root.onDeactivation(spanContext.serialize(), rootContext.serialize(), TimeUnit.MILLISECONDS.toNanos(25));

        root.addStackTrace(tracer, List.of(StackFrame.of("A", "a")), TimeUnit.MILLISECONDS.toNanos(30), callTreePool, 0);
        root.end(callTreePool, 0);

        System.out.println(root);

        assertThat(root.getCount()).isEqualTo(4);
        assertThat(root.getDurationUs()).isEqualTo(30_000);
        assertThat(root.getChildren()).hasSize(1);

        CallTree a = root.getLastChild();
        assertThat(a).isNotNull();
        assertThat(a.getFrame().getMethodName()).isEqualTo("a");
        assertThat(a.getCount()).isEqualTo(4);
        assertThat(a.getDurationUs()).isEqualTo(30_000);
        assertThat(a.getChildren()).hasSize(1);

        CallTree b = a.getLastChild();
        assertThat(b).isNotNull();
        assertThat(b.getFrame().getMethodName()).isEqualTo("b");
        assertThat(b.getCount()).isEqualTo(2);
        assertThat(b.getDurationUs()).isEqualTo(10_000);
        assertThat(b.getChildren()).isEmpty();

        root.spanify();

        assertThat(reporter.getSpans()).hasSize(2);
        assertThat(reporter.getSpans().get(0).getTraceContext().isChildOf(spanContext)).isTrue();
        assertThat(reporter.getSpans().get(1).getTraceContext().isChildOf(rootContext)).isTrue();
    }


    @Test
    void testSpanWithInvertedActivation() {
        TraceContextImpl rootContext = CallTreeTest.rootTraceContext(tracer);

        TraceContextImpl childSpanContext = TraceContextImpl.with64BitId(tracer);
        TraceContextImpl.fromParentContext().asChildOf(childSpanContext, rootContext);

        NoopObjectPool<CallTree.Root> rootPool = NoopObjectPool.ofRecyclable(() -> new CallTree.Root(tracer));
        NoopObjectPool<CallTree> childPool = NoopObjectPool.ofRecyclable(CallTree::new);

        CallTree.Root root = CallTree.createRoot(rootPool, childSpanContext.serialize(), rootContext.getServiceName(), rootContext.getServiceVersion(), 0);
        root.addStackTrace(tracer, Collections.singletonList(StackFrame.of("A", "a")), 10_000, childPool, 0);

        root.onActivation(rootContext.serialize(), 20_000);
        root.onDeactivation(rootContext.serialize(), childSpanContext.serialize(), 30_000);

        root.addStackTrace(tracer, Collections.singletonList(StackFrame.of("A", "a")), 40_000, childPool, 0);
        root.end(childPool, 0);

        root.spanify();

        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getSpans().get(0).getTraceContext().isChildOf(childSpanContext)).isTrue();
        // the inferred span should not have any span links because this
        // span link would cause a cycle in the trace
        assertThat(reporter.getSpans().get(0).getChildIds().getSize()).isEqualTo(0L);
    }

    @Test
    void testSpanWithNestedActivation() {
        TraceContextImpl rootContext = CallTreeTest.rootTraceContext(tracer);

        NoopObjectPool<CallTree.Root> rootPool = NoopObjectPool.ofRecyclable(() -> new CallTree.Root(tracer));
        NoopObjectPool<CallTree> childPool = NoopObjectPool.ofRecyclable(CallTree::new);

        CallTree.Root root = CallTree.createRoot(rootPool, rootContext.serialize(), rootContext.getServiceName(), rootContext.getServiceVersion(), 0);
        root.addStackTrace(tracer, Collections.singletonList(StackFrame.of("A", "a")), 10_000, childPool, 0);

        root.onActivation(rootContext.serialize(), 20_000);
        root.onDeactivation(rootContext.serialize(), rootContext.serialize(), 30_000);

        root.addStackTrace(tracer, Collections.singletonList(StackFrame.of("A", "a")), 40_000, childPool, 0);
        root.end(childPool, 0);

        root.spanify();

        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getSpans().get(0).getTraceContext().isChildOf(rootContext)).isTrue();
        // the inferred span should not have any span links because this
        // span link would cause a cycle in the trace
        assertThat(reporter.getSpans().get(0).getChildIds().getSize()).isEqualTo(0L);
    }

}
