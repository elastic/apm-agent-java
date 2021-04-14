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

import co.elastic.apm.agent.profiler.SamplingProfiler;
import co.elastic.apm.agent.profiler.SystemNanoClock;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class ProfilerBenchmark extends AbstractMockApmServerBenchmark {

    private SamplingProfiler samplingProfiler;

    public ProfilerBenchmark() {
        super(true);
    }

    public static void main(String[] args) throws Exception {
        run(ProfilerBenchmark.class);
    }

    @Setup
    public void setUp() throws Exception {
        samplingProfiler = new SamplingProfiler(tracer,
            new SystemNanoClock(),
            new File(getClass().getClassLoader().getResource("apm-activation-events.bin").toURI()),
            new File(getClass().getClassLoader().getResource("apm-traces.jfr").toURI()));
    }

    @TearDown
    public void tearDownProfilerBenchmark() throws Exception {
        samplingProfiler.stop();
    }

    @Benchmark
    public void processTraces() throws IOException {
        samplingProfiler.skipToEndOfActivationEventsFile();
        samplingProfiler.processTraces();
        samplingProfiler.clearProfiledThreads();
    }

}
