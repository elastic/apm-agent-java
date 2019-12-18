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
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.StackFrame;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CallTreeTest {

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
    void testCallTree() {
        CallTree.Root root = CallTree.createRoot(TraceContext.with64BitId(mock(ElasticApmTracer.class)).getTraceContext(), 0);
        root.addStackTrace(List.of(StackFrame.of("A", "a")), 0);
        root.addStackTrace(List.of(StackFrame.of("A", "b"), StackFrame.of("A", "a")), TimeUnit.MILLISECONDS.toNanos(10));
        root.addStackTrace(List.of(StackFrame.of("A", "a")), TimeUnit.MILLISECONDS.toNanos(20));
        root.end();

        System.out.println(root);

        assertThat(root.getCount()).isEqualTo(3);
        assertThat(root.getChildren()).hasSize(1);

        CallTree a = root.getLastChild();
        assertThat(a).isNotNull();
        assertThat(a.getFrame().getMethodName()).isEqualTo("a");
        assertThat(a.getCount()).isEqualTo(3);
        assertThat(a.getChildren()).hasSize(1);

        CallTree b = a.getLastChild();
        assertThat(b).isNotNull();
        assertThat(b.getFrame().getMethodName()).isEqualTo("b");
        assertThat(b.getCount()).isEqualTo(1);
        assertThat(b.getChildren()).isEmpty();
    }


    @Test
    void testCallTrees() {
        assertCallTree(new String[]{
            " b b",
            "aaaa"
        }, new Object[][] {
            {"a",   4},
            {"  b", 1},
            {"  b", 1}
        });
        assertCallTree(new String[]{
            " c  ",
            " bb ",
            "aaaa"
        }, new Object[][] {
            {"a",     4},
            {"  b",   2},
            {"    c", 1}
        }, new Object[][] {
            {"a",     3},
            {"  b",   1},
            {"    c", 0}
        });
        assertCallTree(new String[]{
            " d ",
            " c ",
            " b ",
            "aaa"
        }, new Object[][] {
            {"a",       3},
            {"  b",     1},
            {"    c",   1},
            {"      d", 1}
        }, new Object[][] {
            {"a",   2},
            {"  d", 0, List.of("c", "b")}
        });

        assertCallTree(new String[]{
            "cccc",
            "aabb"
        }, new Object[][] {
            {"a",   2},
            {"  c", 2},
            {"b",   2},
            {"  c", 2},
        });
        assertCallTree(new String[]{
            "bcbc",
            "bbbb",
            "aaaa"
        }, new Object[][] {
            {"a",     4},
            {"  b",   4},
            {"    b", 1},
            {"    c", 1},
            {"    b", 1},
            {"    c", 1},
        }, null);
        assertCallTree(new String[]{
            "     c ee   ",
            "   bbb dd   ",
            " a aaaaaa a ",
            "1 2      2 1"
        }, new Object[][] {
            {"a",     8},
            {"  b",   3},
            {"    c", 1},
            {"  d",   2},
            {"    e", 2},
        }, new Object[][] {
            {"1",        11},
            {"  a",       9},
            {"    2",     7},
            {"      b",   2},
            {"        c", 0},
            {"    e",     1, List.of("d")},
        });
    }

    private void assertCallTree(String[] stackTraces, Object[][] expectedTree) {
        assertCallTree(stackTraces, expectedTree, null);
    }
    private void assertCallTree(String[] stackTraces, Object[][] expectedTree, @Nullable Object[][] expectedSpans) {
        CallTree.Root root = getCallTree(tracer, stackTraces);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < expectedTree.length; i++) {
            Object[] objects = expectedTree[i];
            result.append(objects[0]).append(" ").append(objects[1]);
            if (i != expectedTree.length -1) {
                result.append("\n");
            }
        }

        String expectedString = root.toString()
            .replace(CallTreeTest.class.getName() + ".", "");
        expectedString = Arrays.stream(expectedString.split("\n"))
            // skip root node
            .skip(1)
            // trim first two spaces
            .map(s -> s.substring(2))
            .collect(Collectors.joining("\n"));

        assertThat(result.toString()).isEqualTo(expectedString);

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
                List<String> stackTrace = expectedSpan.length == 3 ? (List<String>) expectedSpan[2] : null;
                int nestingLevel = getNestingLevel((String) expectedSpan[0]);
                String parentName = getParentName(expectedSpans, i, nestingLevel);
                assertThat(spans).containsKey(spanName);
                assertThat(spans).containsKey(parentName);
                AbstractSpan<?> span = spans.get(spanName);
                assertThat(span.getTraceContext().isChildOf(spans.get(parentName)));
                assertThat(span.getDuration()).isEqualTo(durationMs * 1000);
                if (stackTrace != null) {
                    assertThat(((Span) span).getStackFrames()
                        .stream()
                        .map(StackFrame::getMethodName)
                        .collect(Collectors.toList()))
                        .isEqualTo(stackTrace);
                }
            }
        }
        reporter.reset();
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

    public static CallTree.Root getCallTree(ElasticApmTracer tracer, String[] stackTraces) {
        SamplingProfiler profiler = tracer.getLifecycleListener(ProfilingFactory.class).getProfiler();
        profiler.setProfilingSessionOngoing(true);
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, ConstantSampler.of(true), 0, null)
            .activate();
        transaction.getTraceContext().getClock().init(0, System.nanoTime());
        profiler.processActivationEventsUpTo(System.nanoTime());
        CallTree.Root root = profiler.getRoot();
        assertThat(root).isNotNull();
        long nanoTime = 0;
        Map<String, AbstractSpan<?>> spanMap = new HashMap<>();
        for (int i = 0; i < stackTraces[0].length(); i++) {
            nanoTime = i * TimeUnit.MILLISECONDS.toNanos(10);
            List<StackFrame> trace = new ArrayList<>();
            for (String stackTrace : stackTraces) {
                char c = stackTrace.charAt(i);
                if (Character.isDigit(c)) {
                    handleSpanEvent(tracer, spanMap, Character.toString(c), nanoTime);
                    profiler.processActivationEventsUpTo(System.nanoTime());
                    break;
                } else if (!Character.isSpaceChar(c)) {
                    trace.add(StackFrame.of(CallTreeTest.class.getName(), Character.toString(c)));
                }
            }
            if (!trace.isEmpty()) {
                root.addStackTrace(trace, nanoTime);
            }
        }
        transaction.deactivate().end(nanoTime / 1000);
        root.end();
        profiler.clear();
        return root;
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
