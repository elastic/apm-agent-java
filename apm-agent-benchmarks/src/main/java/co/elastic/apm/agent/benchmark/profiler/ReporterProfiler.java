/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.benchmark.profiler;

import co.elastic.apm.agent.report.Reporter;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.Defaults;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ScalarResult;
import org.openjdk.jmh.runner.options.TimeValue;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ReporterProfiler implements InternalProfiler {

    private long reportedCountStart;
    private long droppedCountStart;
    @Nullable
    private Long receivedBytesStart;
    @Nullable
    private Long receivedPayloadsStart;


    public ReporterProfiler() {
    }

    private Reporter getReporter() {
        return (Reporter) System.getProperties().get(Reporter.class.getName());
    }

    @Override
    public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
        final Reporter reporter = getReporter();
        if (reporter != null) {
            reportedCountStart = reporter.getReported();
            droppedCountStart = reporter.getDropped();
            receivedBytesStart = getLong("server.received.bytes");
            receivedPayloadsStart = getLong("server.received.payloads");
        }
    }

    private Long getLong(String propertyName) {
        return (Long) System.getProperties().get(propertyName);
    }

    @Override
    public Collection<? extends Result> afterIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams, IterationResult result) {
        List<ScalarResult> results = new ArrayList<>();
        final TimeValue time = iterationParams.getTime();
        final Reporter reporter = getReporter();
        if (reporter != null) {
            final double iterationDurationNs = time.convertTo(TimeUnit.NANOSECONDS);

            final long reportedDuringThisIteration = reporter.getReported() - reportedCountStart;
            if (reportedDuringThisIteration > 0) {
                double reportsPerSecond = perSecond(iterationDurationNs, reportedDuringThisIteration);
                results.add(new ScalarResult(Defaults.PREFIX + "reporter.reported", reportsPerSecond, "events/s", AggregationPolicy.AVG));
            }

            final long droppedDuringThisIteration = reporter.getDropped() - droppedCountStart;
            if (droppedDuringThisIteration >0) {
                double dropsPerSecond = perSecond(iterationDurationNs, droppedDuringThisIteration);
                results.add(new ScalarResult(Defaults.PREFIX + "reporter.dropped", dropsPerSecond, "events/s", AggregationPolicy.AVG));
            }

            if (receivedBytesStart != null) {
                long receivedBytesDuringThisIteration = getLong("server.received.bytes") - receivedBytesStart;
                results.add(new ScalarResult(Defaults.PREFIX + "server.received.bytes", perSecond(iterationDurationNs,
                    receivedBytesDuringThisIteration), "bytes/s", AggregationPolicy.AVG));
            }

            if (receivedPayloadsStart != null) {
                long receivedPayloadsDuringThisIteration = getLong("server.received.payloads") - receivedPayloadsStart;
                results.add(new ScalarResult(Defaults.PREFIX + "server.received.payloads", perSecond(iterationDurationNs,
                    receivedPayloadsDuringThisIteration), "payloads/s", AggregationPolicy.AVG));
            }
        }
        return results;
    }

    private double perSecond(double iterationDurationNs, long deltaThisIteration) {
        return deltaThisIteration / iterationDurationNs * TimeUnit.SECONDS.toNanos(1);
    }

    @Override
    public String getDescription() {
        return "APM Server reporter profiler";
    }
}
