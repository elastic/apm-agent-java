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
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.transaction.AbstractSpanImpl;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.StackFrame;
import co.elastic.apm.agent.impl.transaction.TraceContextImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.objectpool.NoopObjectPool;
import co.elastic.apm.agent.objectpool.ObservableObjectPool;
import co.elastic.apm.agent.objectpool.impl.ListBasedObjectPool;
import co.elastic.apm.agent.testutils.DisabledOnAppleSilicon;
import co.elastic.apm.agent.tracer.configuration.TimeDuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.condition.OS;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
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
import static org.mockito.Mockito.doReturn;

@DisabledOnOs(OS.WINDOWS)
@DisabledOnAppleSilicon
@DisabledForJreRange(min = JRE.JAVA_24)
class CallTreeTest {

    private MockReporter reporter;
    private ElasticApmTracer tracer;
    private ProfilingConfiguration profilerConfig;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        // disable scheduled profiling to not interfere with this test
        profilerConfig = config.getConfig(ProfilingConfiguration.class);
        doReturn(true).when(profilerConfig).isProfilingEnabled();
        tracer = MockTracer.createRealTracer(reporter, config, false);
    }

    @AfterEach
    void tearDown() throws IOException {
        Objects.requireNonNull(tracer.getLifecycleListener(ProfilingFactory.class)).getProfiler().clear();
        tracer.stop();
    }

    @Test
    void testCallTree() {
        TraceContextImpl traceContext = TraceContextImpl.with64BitId(MockTracer.create());
        CallTree.Root root = CallTree.createRoot(NoopObjectPool.ofRecyclable(() -> new CallTree.Root(tracer)), traceContext.serialize(), traceContext.getServiceName(), traceContext.getServiceVersion(), 0);
        ObservableObjectPool<CallTree> callTreePool = ListBasedObjectPool.ofRecyclable(new ArrayList<>(), Integer.MAX_VALUE, CallTree::new);
        root.addStackTrace(tracer, List.of(StackFrame.of("A", "a")), 0, callTreePool, 0);
        root.addStackTrace(tracer, List.of(StackFrame.of("A", "b"), StackFrame.of("A", "a")), TimeUnit.MILLISECONDS.toNanos(10), callTreePool, 0);
        root.addStackTrace(tracer, List.of(StackFrame.of("A", "b"), StackFrame.of("A", "a")), TimeUnit.MILLISECONDS.toNanos(20), callTreePool, 0);
        root.addStackTrace(tracer, List.of(StackFrame.of("A", "a")), TimeUnit.MILLISECONDS.toNanos(30), callTreePool, 0);
        root.end(callTreePool, 0);

        System.out.println(root);

        assertThat(root.getCount()).isEqualTo(4);
        assertThat(root.getDepth()).isEqualTo(0);
        assertThat(root.getChildren()).hasSize(1);

        CallTree a = root.getLastChild();
        assertThat(a).isNotNull();
        assertThat(a.getFrame().getMethodName()).isEqualTo("a");
        assertThat(a.getCount()).isEqualTo(4);
        assertThat(a.getChildren()).hasSize(1);
        assertThat(a.getDepth()).isEqualTo(1);
        assertThat(a.isSuccessor(root)).isTrue();

        CallTree b = a.getLastChild();
        assertThat(b).isNotNull();
        assertThat(b.getFrame().getMethodName()).isEqualTo("b");
        assertThat(b.getCount()).isEqualTo(2);
        assertThat(b.getChildren()).isEmpty();
        assertThat(b.getDepth()).isEqualTo(2);
        assertThat(b.isSuccessor(a)).isTrue();
        assertThat(b.isSuccessor(root)).isTrue();
    }

    @Test
    void testGiveEmptyChildIdsTo() {
        CallTree rich = new CallTree();
        rich.addChildId(42, 0L);
        CallTree robinHood = new CallTree();
        CallTree poor = new CallTree();

        rich.giveLastChildIdTo(robinHood);
        robinHood.giveLastChildIdTo(poor);
        // list is not null but empty, expecting no exception
        robinHood.giveLastChildIdTo(rich);

        assertThat(rich.hasChildIds()).isFalse();
        assertThat(robinHood.hasChildIds()).isFalse();
        assertThat(poor.hasChildIds()).isTrue();
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
    void testDeactivationBeforeEnd() throws Exception {
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
     * [1           ]    [1           ]
     *  [a         ]      [a         ]
     *   [2   ] [3]        [b    ][3]   <- b is supposed to stealChildIdsFom(a)
     *    [b   ]           [2   ]          however, it should only steal 2, not 3
     */
    @Test
    void testDectivationBeforeEnd2() throws Exception {
        assertCallTree(new String[]{
            "   bbbb b     ",
            " a aaaa a a a ",
            "1 2    2 3 3 1"
        }, new Object[][] {
            {"a",       8},
            {"  b",     5},
        }, new Object[][] {
            {"1",       13},
            {"  a",     11},
            {"    b",    6},
            {"      2",  5},
            {"    3",    2},
        });
    }

    /*
     *  [a       ]   [a        ]
     *   [1]           [1]
     *       [2]           [c ]
     *        [b]          [b ]  <- b should steal 2 but not 1 from a
     *        [c]          [2]
     */
    @Test
    void testDectivationBeforeEnd_DontStealChildIdsOfUnrelatedActivations() throws Exception {
        Map<String, AbstractSpanImpl<?>> spans = assertCallTree(new String[]{
            "      c c ",
            "      b b ",
            "a   a a aa",
            " 1 1 2 2  "
        }, new Object[][]{
            {"a",     5},
            {"  b",   2},
            {"    c", 2},
        }, new Object[][]{
            {"a",      9},
            {"  1",     2},
            {"  c",     3, List.of("b")},
            {"    2",   2},
        });
        assertThat(spans.get("a").getChildIds().getSize()).isEqualTo(1);
        assertThat(spans.get("c").getChildIds().getSize()).isEqualTo(1);
    }

    /*
     *  [a         ]   [a         ]
     *   [1]            [1]
     *       [2  ]           [c  ]  <- this is an open issue: c should start when 2 starts but starts with 3 starts
     *        [3]           [2  ]
     *         [c ]          [3]
     */
    @Test
    void testDectivationBeforeEnd_DontStealChildIdsOfUnrelatedActivations_Nested() throws Exception {
        Map<String, AbstractSpanImpl<?>> spans = assertCallTree(new String[]{
            "       c  c ",
            "       b  b ",
            "a   a  a  aa",
            " 1 1 23 32  "
        }, new Object[][]{
            {"a",     5},
            {"  b",   2},
            {"    c", 2},
        }, new Object[][]{
            {"a",      11},
            {"  1",     2},
            {"  c",     4, List.of("b")},
            {"    2",   4},
            {"      3", 2},
        });
        assertThat(spans.get("a").getChildIds().getSize()).isEqualTo(1);
        assertThat(spans.get("c").getChildIds().getSize()).isEqualTo(1);
    }

    /*
     * [a ]      [a  ]
     * [b[1] - > [b[1]
     */
    @Test
    void testActivationAfterMethodEnds() throws Exception {
        assertCallTree(new String[]{
            "bb   ",
            "aa a ",
            "  1 1"
        }, new Object[][] {
            {"a",   3},
            {"  b", 2},
        }, new Object[][] {
            {"a",   3},
            {"  b", 1},
            {"  1", 2}
        });
    }

    /*
     * [a   ]
     * [b[1]
     */
    @Test
    void testActivationBetweenMethods() throws Exception {
        assertCallTree(new String[]{
            "bb   ",
            "aa  a",
            "  11 "
        }, new Object[][] {
            {"a",   3},
            {"  b", 2},
        }, new Object[][] {
            {"a",   4},
            {"  b", 1},
            {"  1", 1},
        });
    }

    /*
     * [a   ]
     * [b[1]
     *  c
     */
    @Test
    void testActivationBetweenMethods_AfterFastMethod() throws Exception {
        assertCallTree(new String[]{
            " c   ",
            "bb   ",
            "aa  a",
            "  11 "
        }, new Object[][] {
            {"a",   3},
            {"  b", 2},
        }, new Object[][] {
            {"a",   4},
            {"  b", 1},
            {"  1", 1},
        });
    }

    /*
     * [a ]
     * [b]
     *  1
     */
    @Test
    void testActivationBetweenFastMethods() throws Exception {
        assertCallTree(new String[]{
            "c  d   ",
            "b  b   ",
            "a  a  a",
            " 11 22 "
        }, new Object[][] {
            {"a",   3},
            {"  b", 2},
        }, new Object[][] {
            {"a",     6},
            {"  b",   3},
            {"    1", 1},
            {"  2",   1},
        });
    }

/*    *//*
     * [a       ]
     * [b] [1 [c]
     *//*
    @Test
    void testActivationBetweenMethods_WithCommonAncestor() throws Exception {
        assertCallTree(new String[]{
            "  c     f  g ",
            "bbb  e  d  dd",
            "aaa  a  a  aa",
            "   11 22 33  "
        }, new Object[][] {
            {"a",   7},
            {"  b", 3},
            {"  d", 3},
        }, new Object[][] {
            {"a",     12},
            {"  b",   2},
            {"  1",   1},
            {"  2",   1},
            {"  d",   4},
            {"    3", 1},
        });
    }*/

    /*
     * [a    ]
     *  [1  ]
     *   [2]
     */
    @Test
    void testNestedActivation() throws Exception {
        Map<String, AbstractSpanImpl<?>> spans = assertCallTree(new String[]{
            "a  a  a",
            " 12 21 "
        }, new Object[][] {
            {"a",     3},
        }, new Object[][] {
            {"a",     6},
            {"  1",   4},
            {"    2", 2},
        });
    }

    /*
     * [1         ]
     *  [a][2    ]
     *  [b] [3  ]
     *       [c]
     */
    @Test
    void testNestedActivationAfterMethodEnds_RootChangesToC() throws Exception {
        Map<String, AbstractSpanImpl<?>> spans = assertCallTree(new String[]{
            " bbb        ",
            " aaa  ccc   ",
            "1   23   321"
        }, new Object[][] {
            {"a",        3},
            {"  b",      3},
            {"c",        3},
        }, new Object[][] {
            {"1",       11},
            {"  b",      2, List.of("a")},
            {"  2",      6},
            {"    3",    4},
            {"      c",  2}
        });

        if (spans.get("b").getChildIds() != null) {
            assertThat(spans.get("b").getChildIds().isEmpty()).isTrue();
        }
    }

    /*
     * [1           ]
     *  [a  ][3    ]
     *  [b  ] [4  ]
     *   [2]   [c]
     */
    @Test
    void testRegularActivationFollowedByNestedActivationAfterMethodEnds() throws Exception {
        assertCallTree(new String[]{
            "   d          ",
            " b b b        ",
            " a a a  ccc   ",
            "1 2 2 34   431"
        }, new Object[][] {
            {"a",        3},
            {"  b",      3},
            {"c",        3},
        }, new Object[][] {
            {"1",       13},
            {"  b",      4, List.of("a")},
            {"    2",    2},
            {"  3",      6},
            {"    4",    4},
            {"      c",  2}
        });
    }

    /*
     * [1             ]
     *  [a           ]
     *   [b  ][3    ]
     *    [2]  [4  ]
     *          [c]
     */
    @Test
    void testNestedActivationAfterMethodEnds_CommonAncestorA() throws Exception {
        Map<String, AbstractSpanImpl<?>> spans = assertCallTree(new String[]{
            "  b b b  ccc    ",
            " aa a a  aaa  a ",
            "1  2 2 34   43 1"
        }, new Object[][]{
            {"a",   8},
            {"  b", 3},
            {"  c", 3},
        }, new Object[][]{
            {"1",        15},
            {"  a",      13},
            {"    b",     4},
            {"      2",   2},
            {"    3",     6},
            {"      4",   4},
            {"        c", 2}
        });

        assertThat(spans.get("b").getChildIds().toArray()).containsExactly(spans.get("2").getTraceContext().getId().readLong(0));
        assertThat(spans.get("c").getChildIds()).isNull();
        // only has 3 as a child as 4 is a nested activation
        assertThat(spans.get("a").getChildIds().toArray()).containsExactly(spans.get("3").getTraceContext().getId().readLong(0));
    }

    /*
     * [1       ]
     *  [a]
     *     [2  ]
     *      [b]
     *      [c]
     */
    @Test
    void testActivationAfterMethodEnds_RootChangesToB() throws Exception {
        assertCallTree(new String[]{
            "     ccc  ",
            " aaa bbb  ",
            "1   2   21"
        }, new Object[][] {
            {"a",     3},
            {"b",     3},
            {"  c",   3},
        }, new Object[][] {
            {"1",     9},
            {"  a",   2},
            {"  2",   4},
            {"    c", 2, List.of("b")}
        });
    }

    /*
     * [1       ]
     *  [a]
     *     [2  ]
     *      [b]
     */
    @Test
    void testActivationAfterMethodEnds_RootChangesToB2() throws Exception {
        assertCallTree(new String[]{
            " aaa bbb  ",
            "1   2   21"
        }, new Object[][] {
            {"a",     3},
            {"b",     3},
        }, new Object[][] {
            {"1",     9},
            {"  a",   2},
            {"  2",   4},
            {"    b", 2}
        });
    }


    /*
     * [1]
     *  [a]
    @Test
    void testActivationBeforeCallTree() throws Exception {
        assertCallTree(new String[]{
            " aaa",
            "1 1 "
        }, new Object[][] {
            {"a",   3},
        }, new Object[][] {
            {"a",   3},
            {"  1", 2},
        });
    }     */


    /*
     * [1       ]
     *  [a     ]
     *     [2  ]
     *      [b]
     *      [c]
     */
    @Test
    void testActivationAfterMethodEnds_SameRootDeeperStack() throws Exception {
        assertCallTree(new String[]{
            "     ccc  ",
            " aaa aaa  ",
            "1   2   21"
        }, new Object[][] {
            {"a",     6},
            {"  c",   3},
        }, new Object[][] {
            {"1",       9},
            {"  a",     6},
            {"    2",   4},
            {"      c", 2}
        });
    }

    /*
     * [1     ]
     *  [a   ]
     *   [2 ]
     *    [b]
     */
    @Test
    void testActivationBeforeMethodStarts() throws Exception {
        assertCallTree(new String[]{
            "   bbb   ",
            " a aaa a ",
            "1 2   2 1"
        }, new Object[][] {
            {"a",       5},
            {"  b",     3},
        }, new Object[][] {
            {"1",       8},
            {"  a",     6},
            {"    2",   4},
            {"      b", 2}
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

    @Test
    void testCallTreeActivationAsParentOfFastSpan() throws Exception {
        assertCallTree(new String[]{
            "    b    ",
            " aa a aa ",
            "1  2 2  1"
        }, new Object[][]{
            {"a",     5}
        }, new Object[][]{
            {"1",     8},
            {"  a",   6},
            {"    2", 2},
        });
    }

    @Test
    void testCallTreeActivationAsChildOfFastSpan() throws Exception {
        doReturn(TimeDuration.of("50ms")).when(profilerConfig).getInferredSpansMinDuration();
        assertCallTree(new String[]{
            "   c  c   ",
            "   b  b   ",
            " aaa  aaa ",
            "1   22   1"
        }, new Object[][]{
            {"a",     6}
        }, new Object[][]{
            {"1",     9},
            {"  a",   7},
            {"    2", 1},
        });
    }

    @Test
    void testCallTreeActivationAsLeaf() throws Exception {
        assertCallTree(new String[]{
            " aa  aa ",
            "1  22  1"
        }, new Object[][]{
            {"a",     4}
        }, new Object[][]{
            {"1",     7},
            {"  a",   5},
            {"    2", 1},
        });
    }


    @Test
    void testCallTreeMultipleActivationsAsLeaf() throws Exception {
        assertCallTree(new String[]{
            " aa  aaa  aa ",
            "1  22   33  1"
        }, new Object[][]{
            {"a",     7}
        }, new Object[][]{
            {"1",    12},
            {"  a",  10},
            {"    2", 1},
            {"    3", 1},
        });
    }

    @Test
    void testCallTreeMultipleActivationsAsLeafWithExcludedParent() throws Exception {
        doReturn(TimeDuration.of("50ms")).when(profilerConfig).getInferredSpansMinDuration();
        // min duration 4
        assertCallTree(new String[]{
            "  b  b c  c  ",
            " aa  aaa  aa ",
            "1  22   33  1"
        }, new Object[][]{
            {"a",     7}
        }, new Object[][]{
            {"1",    12},
            {"  a",  10},
            {"    2", 1},
            {"    3", 1},
        });
    }

    @Test
    void testCallTreeMultipleActivationsWithOneChild() throws Exception {
        assertCallTree(new String[]{
            "         bb    ",
            " aa  aaa aa aa ",
            "1  22   3  3  1"
        }, new Object[][]{
            {"a",     9},
            {"  b",   2}
        }, new Object[][]{
            {"1",     14},
            {"  a",   12},
            {"    2",  1},
            {"    3",  3},
            {"      b",1},
        });
    }

    /*
     * [1   ]     [1   ]
     *  [2]   ->   [a ]
     *   [a]       [2]
     *
     * Note: this test is currently failing
     */
    @Test
    @Disabled("fix me")
    void testNestedActivationBeforeCallTree() throws Exception {
        assertCallTree(new String[]{
            "  aaa ",
            "12 2 1"
        }, new Object[][]{
            {"a",     3},
        }, new Object[][]{
            {"1",     5},
            {"  a",   3}, // a is actually a child of the transaction
            {"    2", 2}, // 2 is not within the child_ids of a
        });
    }

    private void assertCallTree(String[] stackTraces, Object[][] expectedTree) throws Exception {
        assertCallTree(stackTraces, expectedTree, null);
    }

    private Map<String, AbstractSpanImpl<?>> assertCallTree(String[] stackTraces, Object[][] expectedTree, @Nullable Object[][] expectedSpans) throws Exception {
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
            Map<String, AbstractSpanImpl<?>> spans = reporter.getSpans()
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
                AbstractSpanImpl<?> span = spans.get(spanName);
                assertThat(span.isChildOf(spans.get(parentName)))
                    .withFailMessage("Expected %s (%s) to be a child of %s (%s) but was %s (%s)",
                        spanName, span.getTraceContext().getId(),
                        parentName, spans.get(parentName).getTraceContext().getId(),
                        reporter.getSpans()
                            .stream()
                            .filter(s -> s.getTraceContext().getId().equals(span.getTraceContext().getParentId())).findAny()
                            .map(SpanImpl::getNameAsString)
                            .orElse(null),
                        span.getTraceContext().getParentId())
                    .isTrue();
                assertThat(spans.get(parentName).isChildOf(span))
                    .withFailMessage("Expected %s (%s) to not be a child of %s (%s) but was %s (%s)",
                        parentName, spans.get(parentName).getTraceContext().getId(),
                        spanName, span.getTraceContext().getId(),
                        reporter.getSpans()
                            .stream()
                            .filter(s -> s.getTraceContext().getId().equals(span.getTraceContext().getParentId())).findAny()
                            .map(SpanImpl::getNameAsString)
                            .orElse(null),
                        span.getTraceContext().getParentId())
                    .isFalse();
                assertThat(span.getDuration())
                    .describedAs("Unexpected duration for span %s", span)
                    .isEqualTo(durationMs * 1000);
                assertThat(Objects.requireNonNullElse(((SpanImpl) span).getStackFrames(), List.<StackFrame>of())
                    .stream()
                    .map(StackFrame::getMethodName)
                    .collect(Collectors.toList()))
                    .isEqualTo(stackTrace);
            }
            return spans;
        }
        return null;
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
        assertThat(profilingFactory).isNotNull();

        SamplingProfiler profiler = profilingFactory.getProfiler();
        FixedNanoClock nanoClock = (FixedNanoClock) profilingFactory.getNanoClock();
        nanoClock.setNanoTime(0);
        profiler.setProfilingSessionOngoing(true);
        TransactionImpl transaction = tracer
            .startRootTransaction(ConstantSampler.of(true), 0, null)
            .withName("Call Tree Root")
            .activate();
        transaction.getTraceContext().getClock().init(0, 0);
        Map<String, AbstractSpanImpl<?>> spanMap = new HashMap<>();
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
            long millis = tracer.getConfig(ProfilingConfiguration.class).getInferredSpansMinDuration().getMillis();
            root.addStackTrace(tracer, stackTraceEvent.trace, stackTraceEvent.nanoTime, callTreePool, TimeUnit.MILLISECONDS.toNanos(millis));
        }
        transaction.deactivate().end(nanoClock.nanoTime() / 1000);
        assertThat(root).isNotNull();
        root.end(callTreePool, 0);
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

    private static void handleSpanEvent(ElasticApmTracer tracer, Map<String, AbstractSpanImpl<?>> spanMap, String name, long nanoTime) {
        if (!spanMap.containsKey(name)) {
            SpanImpl span = tracer.getActive().createSpan(nanoTime / 1000).appendToName(name).activate();
            spanMap.put(name, span);
        } else {
            spanMap.get(name).deactivate().end(nanoTime / 1000);
        }
    }

    public static TraceContextImpl rootTraceContext(ElasticApmTracer tracer) {
        TraceContextImpl traceContext = TraceContextImpl.with64BitId(tracer);
        traceContext.asRootSpan(ConstantSampler.of(true));
        return traceContext;
    }
}
