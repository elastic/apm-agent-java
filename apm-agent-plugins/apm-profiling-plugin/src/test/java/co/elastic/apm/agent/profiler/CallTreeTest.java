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
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.StackFrame;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.objectpool.NoopObjectPool;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.ListBasedObjectPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CallTreeTest {

    private MockReporter reporter;
    private ElasticApmTracer tracer;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        // disable scheduled profiling to not interfere with this test
        when(config.getConfig(ProfilingConfiguration.class).isProfilingEnabled()).thenReturn(false);
        tracer = MockTracer.createRealTracer(reporter, config);
    }

    @AfterEach
    void tearDown() {
        tracer.stop();
    }

    @Test
    void testCallTree() throws Exception {
        TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        CallTree.Root root = CallTree.createRoot(NoopObjectPool.ofRecyclable(() -> new CallTree.Root(tracer)), traceContext.serialize(), traceContext.getServiceName(), 0);
        ObjectPool<CallTree> callTreePool = ListBasedObjectPool.ofRecyclable(new ArrayList<>(), Integer.MAX_VALUE, CallTree::new);
        root.addStackTrace(tracer, List.of(StackFrame.of("A", "a")), 0, callTreePool, 0);
        root.addStackTrace(tracer, List.of(StackFrame.of("A", "b"), StackFrame.of("A", "a")), TimeUnit.MILLISECONDS.toNanos(10), callTreePool, 0);
        root.addStackTrace(tracer, List.of(StackFrame.of("A", "b"), StackFrame.of("A", "a")), TimeUnit.MILLISECONDS.toNanos(20), callTreePool, 0);
        root.addStackTrace(tracer, List.of(StackFrame.of("A", "a")), TimeUnit.MILLISECONDS.toNanos(30), callTreePool, 0);
        root.end(callTreePool, 0);

        System.out.println(root);

        assertThat(root.getCount()).isEqualTo(4);
        assertThat(root.getChildren()).hasSize(1);

        CallTree a = root.getLastChild();
        assertThat(a).isNotNull();
        assertThat(a.getFrame().getMethodName()).isEqualTo("a");
        assertThat(a.getCount()).isEqualTo(4);
        assertThat(a.getChildren()).hasSize(1);

        CallTree b = a.getLastChild();
        assertThat(b).isNotNull();
        assertThat(b.getFrame().getMethodName()).isEqualTo("b");
        assertThat(b.getCount()).isEqualTo(2);
        assertThat(b.getChildren()).isEmpty();
    }

    @Test
    void testTwoDistinctInvocationsOfMethodBShouldNotBeFoldedIntoOne() throws Exception {
        assertCallTree(new String[]{
            " bb bb",
            "aaaaaa"
        }, new Object[][] {
            {"a",   6},
            {"  b", 2},
            {"  b", 2}
        });
    }

    @Test
    void testBasicCallTree() throws Exception {
        assertCallTree(new String[]{
            " cc ",
            " bbb",
            "aaaa"
        }, new Object[][] {
            {"a",     4},
            {"  b",   3},
            {"    c", 2}
        }, new Object[][] {
            {"a",     3},
            {"  b",   2},
            {"    c", 1}
        });
    }

    @Test
    void testShouldNotCreateInferredSpansForPillarsAndLeafShouldHaveStacktrace() throws Exception {
        assertCallTree(new String[]{
            " dd ",
            " cc ",
            " bb ",
            "aaaa"
        }, new Object[][] {
            {"a",       4},
            {"  b",     2},
            {"    c",   2},
            {"      d", 2}
        }, new Object[][] {
            {"a",   3},
            {"  d", 1, List.of("c", "b")}
        });
    }

    @Test
    void testRemoveNodesWithCountOne() throws Exception {
        assertCallTree(new String[]{
            " b ",
            "aaa"
        }, new Object[][] {
            {"a",  3}
        }, new Object[][] {
            {"a",  2}
        });
    }

    @Test
    void testSameTopOfStackDifferentBottom() throws Exception {
        assertCallTree(new String[]{
            "cccc",
            "aabb"
        }, new Object[][] {
            {"a",   2},
            {"  c", 2},
            {"b",   2},
            {"  c", 2},
        });
    }

    @Test
    void testStackTraceWithRecursion() throws Exception {
        assertCallTree(new String[]{
            "bbccbbcc",
            "bbbbbbbb",
            "aaaaaaaa"
        }, new Object[][] {
            {"a",     8},
            {"  b",   8},
            {"    b", 2},
            {"    c", 2},
            {"    b", 2},
            {"    c", 2},
        });
    }

    @Test
    void testFirstInferredSpanShouldHaveNoStackTrace() throws Exception {
        assertCallTree(new String[]{
            "bb",
            "aa"
        }, new Object[][] {
            {"a",   2},
            {"  b", 2},
        }, new Object[][] {
            {"b",   1},
        });
    }

    @Test
    void testCallTreeWithSpanActivations() throws Exception {
        assertCallTree(new String[]{
            "    cc ee   ",
            "   bbb dd   ",
            " a aaaaaa a ",
            "1 2      2 1"
        }, new Object[][] {
            {"a",     8},
            {"  b",   3},
            {"    c", 2},
            {"  d",   2},
            {"    e", 2},
        }, new Object[][] {
            {"1",        11},
            {"  a",       9},
            {"    2",     7},
            {"      b",   2},
            {"        c", 1},
            {"      e",   1, List.of("d")},
        });
    }

    /*
     * [1        ]    [1        ]
     *  [a      ]      [a      ]
     *   [2   ]    ─┐   [b     ]
     *    [b    ]   │   [c    ]
     *    [c   ]    └►  [2   ]
     *    []             []
     */
    @Test
    void testDectivationBeforeEnd() throws Exception {
        assertCallTree(new String[]{
            "   dd      ",
            "   cccc c  ",
            "   bbbb bb ", // <- deactivation for span 2 happens before b and c ends
            " a aaaa aa ", //    that means b and c must have started before 2 has been activated
            "1 2    2  1"  //    but we saw the first stack trace of b only after the activation of 2
        }, new Object[][] {
            {"a",       7},
            {"  b",     6},
            {"    c",   5},
            {"      d", 2},
        }, new Object[][] {
            {"1",          10},
            {"  a",         8},
            {"    b",       7},
            {"      c",     6},
            {"        2",   5},
            {"          d", 1},
        });
    }

    /*
     * [1        ]    [1        ]
     *  [a      ]      [a      ]
     *   [b   ]    ->   [b    ]
     *    [c  ]    ->    [c   ]
     *     [2  ]          [2  ]
     *      []             []
     */
    @Test
    void testDectivationAfterEnd() throws Exception {
        assertCallTree(new String[]{
            "     dd     ",
            "   c ccc    ",
            "  bb bbb    ", // <- deactivation for span 2 happens after b ends
            " aaa aaa aa ", //    that means b must have ended after 2 has been deactivated
            "1   2   2  1"  //    but we saw the last stack trace of b before the deactivation of 2
        }, new Object[][] {
            {"a",       8},
            {"  b",     5},
            {"    c",   4},
            {"      d", 2},
        }, new Object[][] {
            {"1",          11},
            {"  a",         9},
            {"    b",       6},
            {"      c",     5},
            {"        2",   4},
            {"          d", 1},
        });
    }

    private void assertCallTree(String[] stackTraces, Object[][] expectedTree) throws Exception {
        assertCallTree(stackTraces, expectedTree, null);
    }

    private void assertCallTree(String[] stackTraces, Object[][] expectedTree, @Nullable Object[][] expectedSpans) throws Exception {
        CallTree.Root root = getCallTree(tracer, stackTraces);
        StringBuilder expectedResult = new StringBuilder();
        for (int i = 0; i < expectedTree.length; i++) {
            Object[] objects = expectedTree[i];
            expectedResult.append(objects[0]).append(" ").append(objects[1]);
            if (i != expectedTree.length -1) {
                expectedResult.append("\n");
            }
        }

        String actualResult = root.toString()
            .replace(CallTreeTest.class.getName() + ".", "");
        actualResult = Arrays.stream(actualResult.split("\n"))
            // skip root node
            .skip(1)
            // trim first two spaces
            .map(s -> s.substring(2))
            .collect(Collectors.joining("\n"));

        assertThat(actualResult).isEqualTo(expectedResult.toString());

        if (expectedSpans != null) {
            root.spanify();
            Map<String, AbstractSpan<?>> spans = reporter.getSpans()
                .stream()
                .collect(toMap(
                    s -> s.getNameAsString().replaceAll(".*#", ""),
                    Function.identity()));
            assertThat(reporter.getSpans()).hasSize(expectedSpans.length);
            spans.put(null, reporter.getTransactions().get(0));

            for (int i = 0; i < expectedSpans.length; i++) {
                Object[] expectedSpan = expectedSpans[i];
                String spanName = ((String) expectedSpan[0]).trim();
                long durationMs = (int) expectedSpan[1] * 10;
                List<String> stackTrace = expectedSpan.length == 3 ? (List<String>) expectedSpan[2] : List.of();
                int nestingLevel = getNestingLevel((String) expectedSpan[0]);
                String parentName = getParentName(expectedSpans, i, nestingLevel);
                assertThat(spans).containsKey(spanName);
                assertThat(spans).containsKey(parentName);
                AbstractSpan<?> span = spans.get(spanName);
                // span names denoted by digits are actual spans, not profiler-inferred spans
                // currently, the parent of an actual span can't be an inferred span
                if (!Character.isDigit(spanName.charAt(0))) {
                    assertThat(span.getTraceContext().isChildOf(spans.get(parentName)))
                        .withFailMessage("Expected %s (%s) to be a child of %s (%s) but was %s", spanName, span.getTraceContext().getId(),
                            parentName, spans.get(parentName).getTraceContext().getId(), span.getTraceContext().getParentId())
                        .isTrue();
                }
                assertThat(span.getDuration())
                    .describedAs("Unexpected duration for span %s", span)
                    .isEqualTo(durationMs * 1000);
                assertThat(Objects.requireNonNullElse(((Span) span).getStackFrames(), List.<StackFrame>of())
                    .stream()
                    .map(StackFrame::getMethodName)
                    .collect(Collectors.toList()))
                    .isEqualTo(stackTrace);
            }
        }
    }

    @Nullable
    private String getParentName(@Nonnull Object[][] expectedSpans, int i, int nestingLevel) {
        if (nestingLevel > 0) {
            for (int j = i - 1; j >= 0; j--) {
                String name = (String) expectedSpans[j][0];
                boolean isParent = getNestingLevel(name) == nestingLevel - 1;
                if (isParent) {
                    return name.trim();
                }
            }
        }
        return null;
    }

    private int getNestingLevel(String spanName) {
        // nesting is denoted by two spaces
        return ((spanName).length() - 1) / 2;
    }

    public static CallTree.Root getCallTree(ElasticApmTracer tracer, String[] stackTraces) throws Exception {
        ProfilingFactory profilingFactory = tracer.getLifecycleListener(ProfilingFactory.class);
        SamplingProfiler profiler = profilingFactory.getProfiler();
        FixedNanoClock nanoClock = (FixedNanoClock) profilingFactory.getNanoClock();
        nanoClock.setNanoTime(0);
        profiler.setProfilingSessionOngoing(true);
        Transaction transaction = tracer
            .startRootTransaction(ConstantSampler.of(true), 0, null)
            .withName("Call Tree Root")
            .activate();
        transaction.getTraceContext().getClock().init(0, 0);
        Map<String, AbstractSpan<?>> spanMap = new HashMap<>();
        List<StackTraceEvent> stackTraceEvents = new ArrayList<>();
        for (int i = 0; i < stackTraces[0].length(); i++) {
            nanoClock.setNanoTime(i * TimeUnit.MILLISECONDS.toNanos(10));
            List<StackFrame> trace = new ArrayList<>();
            for (String stackTrace : stackTraces) {
                char c = stackTrace.charAt(i);
                if (Character.isDigit(c)) {
                    handleSpanEvent(tracer, spanMap, Character.toString(c), nanoClock.nanoTime());
                    break;
                } else if (!Character.isSpaceChar(c)) {
                    trace.add(StackFrame.of(CallTreeTest.class.getName(), Character.toString(c)));
                }
            }
            if (!trace.isEmpty()) {
                stackTraceEvents.add(new StackTraceEvent(trace, nanoClock.nanoTime()));
            }
        }
        profiler.consumeActivationEventsFromRingBufferAndWriteToFile();
        long eof = profiler.startProcessingActivationEventsFile();
        CallTree.Root root = null;
        NoopObjectPool<CallTree> callTreePool = NoopObjectPool.ofRecyclable(CallTree::new);
        for (StackTraceEvent stackTraceEvent : stackTraceEvents) {
            profiler.processActivationEventsUpTo(stackTraceEvent.nanoTime, eof);
            if (root == null) {
                root = profiler.getRoot();
                assertThat(root).isNotNull();
            }
            root.addStackTrace(tracer, stackTraceEvent.trace, stackTraceEvent.nanoTime, callTreePool, 0);
        }
        transaction.deactivate().end(nanoClock.nanoTime() / 1000);
        assertThat(root).isNotNull();
        root.end(callTreePool, 0);
        profiler.clear();
        return root;
    }

    private static class StackTraceEvent {

        private final List<StackFrame> trace;
        private final long nanoTime;

        public StackTraceEvent(List<StackFrame> trace, long nanoTime) {

            this.trace = trace;
            this.nanoTime = nanoTime;
        }
    }

    private static void handleSpanEvent(ElasticApmTracer tracer, Map<String, AbstractSpan<?>> spanMap, String name, long nanoTime) {
        if (!spanMap.containsKey(name)) {
            Span span = tracer.getActive().createSpan(nanoTime / 1000).appendToName(name).activate();
            spanMap.put(name, span);
        } else {
            spanMap.get(name).deactivate().end(nanoTime / 1000);
        }
    }

    public static TraceContext rootTraceContext(ElasticApmTracer tracer) {
        TraceContext traceContext = TraceContext.with64BitId(tracer);
        traceContext.asRootSpan(ConstantSampler.of(true));
        return traceContext;
    }
}
