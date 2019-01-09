/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
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

import com.sun.management.OperatingSystemMXBean;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.Defaults;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ScalarResult;

import javax.management.MBeanServerConnection;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CpuProfiler implements InternalProfiler {

    private final OperatingSystemMXBean osMxBean;
    private long beforeProcessCpuTime;

    public CpuProfiler() {
        try {
            MBeanServerConnection mbsc = ManagementFactory.getPlatformMBeanServer();
            osMxBean = ManagementFactory.newPlatformMXBeanProxy(mbsc, ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME,
                com.sun.management.OperatingSystemMXBean.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
        beforeProcessCpuTime = osMxBean.getProcessCpuTime();
    }

    @Override
    public Collection<? extends Result> afterIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams, IterationResult result) {
        List<ScalarResult> results = new ArrayList<>();
        long allOps = result.getMetadata().getAllOps();

        long totalCpuTime = osMxBean.getProcessCpuTime() - beforeProcessCpuTime;

        results.add(new ScalarResult(Defaults.PREFIX + "cpu.time.norm",
            (allOps != 0) ? 1.0 * totalCpuTime / allOps : Double.NaN, "ns/op", AggregationPolicy.AVG));
        return results;
    }

    @Override
    public String getDescription() {
        return "CPU profiling via MBeans";
    }
}
