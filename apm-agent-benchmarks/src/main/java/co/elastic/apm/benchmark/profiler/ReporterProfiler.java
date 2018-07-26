/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.benchmark.profiler;

import co.elastic.apm.report.Reporter;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.Defaults;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ScalarResult;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ReporterProfiler implements InternalProfiler {

    private long reportedCountStart;


    public ReporterProfiler() {
    }

    private Reporter getReporter() {
        Reporter reporter = (Reporter) System.getProperties().get(Reporter.class.getName());
        return reporter;
    }

    @Override
    public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
        final Reporter reporter = getReporter();
        if (reporter != null) {
            reportedCountStart = reporter.getReported();
        }
    }

    @Override
    public Collection<? extends Result> afterIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams, IterationResult result) {
        List<ScalarResult> results = new ArrayList<>();
        final TimeValue time = iterationParams.getTime();
        final Reporter reporter = getReporter();
        if (reporter != null) {
            final long reportedDuringThisIteration = reporter.getReported() - reportedCountStart;
            final double iterationDurationNs = time.convertTo(TimeUnit.NANOSECONDS);
            double reportsPerSecond = reportedDuringThisIteration / iterationDurationNs * TimeUnit.SECONDS.toNanos(1);
            results.add(new ScalarResult(Defaults.PREFIX + "reporter.reported", reportsPerSecond, "events/s", AggregationPolicy.AVG));
        }
        return results;
    }

    @Override
    public String getDescription() {
        return "CPU profiling via MBeans";
    }
}
