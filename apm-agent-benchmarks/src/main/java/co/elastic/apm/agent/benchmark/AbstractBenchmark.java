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
package co.elastic.apm.agent.benchmark;

import co.elastic.apm.agent.benchmark.profiler.CpuProfiler;
import co.elastic.apm.agent.benchmark.profiler.ReporterProfiler;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

@State(Scope.Benchmark)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
// set value = 0 if you want to debug the benchmarks
@Fork(value = 1, jvmArgsAppend = {
    "-Xmx1g",
    "-Xms1g"/*,
    "-XX:+UnlockDiagnosticVMOptions",
    "-XX:+DebugNonSafepoints",
    "-XX:+FlightRecorder",
    "-XX:StartFlightRecording=disk=true," +
        "dumponexit=true," +
        "filename=./recording.jfr," +
        "settings=profile"*/
})
public abstract class AbstractBenchmark {

    /**
     * Convenience benchmark run method
     * <p>
     * For more accurate results, execute {@code mvn clean package} and run the benchmark via
     * {@code java -jar apm-agent-benchmarks/target/benchmarks.jar -prof gc}
     */
    public static void run(Class<? extends AbstractBenchmark> benchmark) throws RunnerException {
        new Runner(new OptionsBuilder()
            .include(benchmark.getSimpleName())
            .measurementTime(TimeValue.seconds(1))
            .warmupTime(TimeValue.seconds(1))
            .addProfiler(GCProfiler.class)
            .addProfiler(CpuProfiler.class)
            .addProfiler(ReporterProfiler.class)
            .build())
            .run();
    }


}
