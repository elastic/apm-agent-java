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
package co.elastic.apm.agent.benchmark;

import co.elastic.apm.agent.impl.transaction.StackFrame;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.profiler.CallTree;
import co.elastic.apm.agent.profiler.asyncprofiler.JfrParser;
import co.elastic.apm.agent.profiler.collections.Int2ObjectHashMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class ProfilerBenchmark extends AbstractBenchmark {

    private JfrParser jfrParser;
    private File file;
    private ArrayList<StackFrame> stackFrames;
    private int stackTraces = 0;
    private BasicStackTraceConsumer basicStackTraceConsumer;
    private Int2ObjectHashMap<CallTree.Root> callTrees = new Int2ObjectHashMap<>();
    private CallTreeStackTraceConsumer callTreeStackTraceConsumer;

    public static void main(String[] args) throws Exception {
        run(ProfilerBenchmark.class);
    }

    @Setup
    public void setUp() throws Exception {
        jfrParser = new JfrParser();
        file = new File(getClass().getClassLoader().getResource("recording.jfr").toURI());
        stackFrames = new ArrayList<>();
        basicStackTraceConsumer = new BasicStackTraceConsumer();
        callTreeStackTraceConsumer = new CallTreeStackTraceConsumer();
    }

    @Benchmark
    public int benchmarkJfrParser() throws Exception {
        jfrParser.parse(file, Collections.emptyList(), WildcardMatcher.matchAllList());
        jfrParser.consumeStackTraces(basicStackTraceConsumer);
        jfrParser.resetState();
        return stackTraces;
    }

    @Benchmark
    public int benchmarkCallTree() throws Exception {
        jfrParser.parse(file, Collections.emptyList(), WildcardMatcher.matchAllList());
        jfrParser.consumeStackTraces(callTreeStackTraceConsumer);
        jfrParser.resetState();
        callTrees.forEach((id, root) -> root.resetState());
        return stackTraces + callTrees.size();
    }

    private class BasicStackTraceConsumer implements JfrParser.StackTraceConsumer {
        @Override
        public void onCallTree(int threadId, long stackTraceId, long nanoTime) {
            jfrParser.resolveStackTrace(stackTraceId, false, stackFrames, 512);
            stackFrames.clear();
            stackTraces++;
        }
    }

    private class CallTreeStackTraceConsumer implements JfrParser.StackTraceConsumer {
        @Override
        public void onCallTree(int threadId, long stackTraceId, long nanoTime) {
            jfrParser.resolveStackTrace(stackTraceId, false, stackFrames, 512);
            CallTree.Root root = callTrees.get(threadId);
            if (root == null) {
                root = new CallTree.Root(null);
                callTrees.put(threadId, root);

            }
            root.addStackTrace(null, stackFrames, nanoTime);
            stackFrames.clear();
            stackTraces++;
        }
    }
}
