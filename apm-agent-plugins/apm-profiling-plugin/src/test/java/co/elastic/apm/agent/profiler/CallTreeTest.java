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

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CallTreeTest {

    @Test
    void testCallTree() {
        CallTree.Root root = CallTree.createRoot(TraceContext.with64BitId(mock(ElasticApmTracer.class)).getTraceContext().copy(), 10, WildcardMatcher.matchAllList(), Collections.emptyList());
        root.addStackTrace(List.of(asFrame("a")));
        root.addStackTrace(List.of(asFrame("b"), asFrame("a")));
        root.addStackTrace(List.of(asFrame("a")));
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
        });
    }

    private void assertCallTree(String[] stackTraces, Object[][] expectedTree) {
        CallTree root = getCallTree(stackTraces);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < expectedTree.length; i++) {
            Object[] objects = expectedTree[i];
            result.append(objects[0]).append(" ").append(objects[1]);
            if (i != expectedTree.length -1) {
                result.append("\n");
            }
        }

        String expectedString = root.toString()
            .replace("CallTreeTest.", "");
        expectedString = Arrays.stream(expectedString.split("\n"))
            // skip root node
            .skip(1)
            // trim first two spaces
            .map(s -> s.substring(2))
            .collect(Collectors.joining("\n"));

        assertThat(result.toString()).isEqualTo(expectedString);
    }

    public static CallTree.Root getCallTree(String[] stackTraces) {
        return getCallTree(mock(ElasticApmTracer.class), stackTraces);
    }

    public static CallTree.Root getCallTree(ElasticApmTracer tracer, String[] stackTraces) {
        TraceContext traceContext = rootTraceContext(tracer);
        CallTree.Root root = CallTree.createRoot(traceContext.getTraceContext().copy(), 10, WildcardMatcher.matchAllList(), Collections.emptyList());
        for (int i = 0; i < stackTraces[0].length(); i++) {
            List<StackTraceElement> trace = new ArrayList<>();
            for (String stackTrace : stackTraces) {
                char c = stackTrace.charAt(i);
                if (!Character.isSpaceChar(c)) {
                    trace.add(asFrame(Character.toString(c)));
                }
            }
            root.addStackTrace(trace);
        }
        root.end();
        return root;
    }

    public static TraceContext rootTraceContext(ElasticApmTracer tracer) {
        TraceContext traceContext = TraceContext.with64BitId(tracer);
        traceContext.asRootSpan(ConstantSampler.of(true));
        return traceContext;
    }

    public static StackTraceElement asFrame(String method) {
        return new StackTraceElement("CallTreeTest", method, null, -1);
    }
}
