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
package co.elastic.apm.agent.impl.circuitbreaker;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;

class GCStressMonitor extends StressMonitor {

    private static final Logger logger = LoggerFactory.getLogger(GCStressMonitor.class);

    private final List<MemoryPoolMXBean> heapMBeans = new ArrayList<>();
    private final StringBuilder latestStressDetectionInfo = new StringBuilder("No stress has been detected so far.");

    GCStressMonitor(ElasticApmTracer tracer) {
        super(tracer);
        discoverMBeans();
    }

    /**
     * We cache the {@link MemoryPoolMXBean}s representing heap memory pools, which should be created at JVM bootstrap
     * and available through the entire JVM lifetime.
     */
    private void discoverMBeans() {
        List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean memoryPoolMXBean : memoryPoolMXBeans) {
            MemoryUsage collectionUsage = memoryPoolMXBean.getCollectionUsage();
            if (collectionUsage != null && !memoryPoolMXBean.getName().toLowerCase().contains("survivor")) {
                // Typically, the collection usage is nonnull for heap pools. The survivor pool may be problematic as
                // JVMs may frequently adjust its size, returning it back to the OS, so we cannot even rely on their max size.
                heapMBeans.add(memoryPoolMXBean);
                logger.debug("Registering a heap memory pool ({}) for stress monitoring", memoryPoolMXBean.getName());
            } else {
                logger.trace("Ignoring a non-heap memory pool ({}) for stress monitoring", memoryPoolMXBean.getName());
            }
        }
    }

    @Override
    boolean isUnderStress() {
        return isThresholdCrossed(circuitBreakerConfiguration.getGcStressThreshold(), true);
    }

    private boolean isThresholdCrossed(double percentageThreshold, boolean updateStressInfoIfCrossed) {
        // We apply the same threshold to all heap pools at the moment. We can rethink that if we find it is not the
        // right way to go.
        for (int i = 0; i < heapMBeans.size(); i++) {
            MemoryPoolMXBean heapPoolMBean = heapMBeans.get(i);
            MemoryUsage memUsageAfterLastGc = heapPoolMBean.getCollectionUsage();
            if (memUsageAfterLastGc != null) {
                long max = memUsageAfterLastGc.getMax();
                if (max > 0) {
                    // The max is not always defined for memory pools, however it is normally defined for the Old Gen
                    // pools, which are really the interesting ones.
                    // Since we are tracking pool state after GC, falling back to committed may be useful: during
                    // stress, committed will quickly grow to the max. As long as committed is less than max, the JVM
                    // will maintain a gap of committed over used (as much as possible), otherwise allocations may fail.
                    // However, should we choose to rely on committed, it will need to be restricted only for the
                    // Old Generation heap pools, which means that if we are to rely on committed, we will be limited
                    // to known pool names only. If we see a need going forward to use this fallback, we can investigate further.
                    long bytesThreshold = (long) (percentageThreshold * max);
                    long used = memUsageAfterLastGc.getUsed();
                    if (bytesThreshold > 0 && used > bytesThreshold) {
                        if (updateStressInfoIfCrossed) {
                            latestStressDetectionInfo.setLength(0);
                            latestStressDetectionInfo.append("Heap pool \"").append(heapPoolMBean.getName())
                                .append("\" usage after the last GC has crossed the configured threshold ")
                                .append(percentageThreshold).append(": ").append(used).append("/").append(max)
                                .append("(used/max)");
                        } else if (logger.isDebugEnabled()) {
                            logger.debug("Heap {} pool usage after the last GC is over the threshold of {}: {}/{} (used/max)",
                                heapPoolMBean.getName(), percentageThreshold, used, max);
                        }
                        return true;
                    } else {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Heap {} pool usage after the last GC is below the threshold of {}: {}/{} (used/max)",
                                heapPoolMBean.getName(), percentageThreshold, used, max);
                        }
                    }
                }
            } else {
                logger.debug("Collection usage cannot be obtained from heap pool MBean {}", heapPoolMBean.getName());
            }
        }
        return false;
    }

    @Override
    boolean isStressRelieved() {
        return !isThresholdCrossed(circuitBreakerConfiguration.getGcReliefThreshold(), false);
    }

    @Override
    String getStressDetectionInfo() {
        return latestStressDetectionInfo.toString();
    }
}
