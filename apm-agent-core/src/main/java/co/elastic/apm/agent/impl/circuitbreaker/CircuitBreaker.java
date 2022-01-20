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

import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.util.ExecutorUtils;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CircuitBreaker extends AbstractLifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);

    private final ThreadPoolExecutor threadPool;
    private final ElasticApmTracer tracer;
    private final CircuitBreakerConfiguration circuitBreakerConfiguration;
    private final long pollInterval;

    private boolean isCurrentlyUnderStress = false;

    private final List<StressMonitor> stressMonitors = new CopyOnWriteArrayList<>();

    public CircuitBreaker(ElasticApmTracer tracer) {
        this.tracer = tracer;
        circuitBreakerConfiguration = tracer.getConfig(CircuitBreakerConfiguration.class);
        pollInterval = circuitBreakerConfiguration.getStressMonitoringPollingIntervalMillis();
        threadPool = ExecutorUtils.createSingleThreadDaemonPool("circuit-breaker", 1);
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        // failsafe loading of stress monitors in isolation
        loadGCStressMonitor(tracer);
        loadSystemCpuStressMonitor(tracer);

        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                pollStressMonitors();
            }
        });
    }

    private void loadGCStressMonitor(ElasticApmTracer tracer) {
        try {
            stressMonitors.add(new GCStressMonitor(tracer));
        } catch (Throwable throwable) {
            logger.error("Failed to load the GC stress monitor. Circuit breaker will not be triggered based on GC events.", throwable);
        }
    }

    private void loadSystemCpuStressMonitor(ElasticApmTracer tracer) {
        try {
            stressMonitors.add(new SystemCpuStressMonitor(tracer));
        } catch (Throwable throwable) {
            logger.error("Failed to load the system CPU stress monitor. Circuit breaker will not be triggered based on system CPU events.", throwable);
        }
    }

    private void pollStressMonitors() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (circuitBreakerConfiguration.isCircuitBreakerEnabled()) {
                    if (isCurrentlyUnderStress) {
                        if (isStressRelieved()) {
                            logger.info("All registered stress monitors indicate that the stress has been relieved");
                            isCurrentlyUnderStress = false;
                            tracer.onStressRelieved();
                        }
                    } else if (isUnderStress()) {
                        isCurrentlyUnderStress = true;
                        tracer.onStressDetected();
                    }
                } else if (isCurrentlyUnderStress) {
                    // to support dynamic disablement under current stress
                    isCurrentlyUnderStress = false;
                    tracer.onStressRelieved();
                }
            } catch (Throwable throwable) {
                // Catch all errors, otherwise the thread will terminate
                logger.error("Error occurred during Circuit Breaker polling", throwable);
            }
            try {
                if (logger.isTraceEnabled()) {
                    logger.trace("Scheduling next stress monitor polling in {}s", pollInterval);
                }
                TimeUnit.MILLISECONDS.sleep(pollInterval);
            } catch (InterruptedException e) {
                logger.info("Stopping the Circuit Breaker thread.");
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean isUnderStress() {
        for (StressMonitor stressMonitor : stressMonitors) {
            try {
                if (stressMonitor.isUnderStress()) {
                    logger.info("Stress detected by {}: {}", stressMonitor.getClass().getName(), stressMonitor.getStressDetectionInfo());
                    return true;
                }
            } catch (Exception e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Failed to poll " + stressMonitor.getClass().getName(), e);
                }
            }
        }
        return false;
    }

    private boolean isStressRelieved() {
        boolean stressRelieved = true;
        for (StressMonitor stressMonitor : stressMonitors) {
            try {
                stressRelieved &= stressMonitor.isStressRelieved();
            } catch (Exception e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Failed to poll " + stressMonitor.getClass().getName(), e);
                }
            }
        }
        return stressRelieved;
    }

    void registerStressMonitor(StressMonitor monitor) {
        stressMonitors.add(monitor);
    }

    void unregisterStressMonitor(StressMonitor monitor) {
        stressMonitors.remove(monitor);
    }

    @Override
    public void stop() {
        this.threadPool.shutdownNow();
    }
}
