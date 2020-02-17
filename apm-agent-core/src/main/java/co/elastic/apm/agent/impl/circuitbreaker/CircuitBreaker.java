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
package co.elastic.apm.agent.impl.circuitbreaker;

import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.util.ExecutorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CircuitBreaker extends AbstractLifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);

    private final ThreadPoolExecutor threadPool;
    private final ElasticApmTracer tracer;

    private final List<StressMonitor> stressMonitors = new CopyOnWriteArrayList<>();

    public CircuitBreaker(ElasticApmTracer tracer) {
        this.tracer = tracer;
        threadPool = ExecutorUtils.createSingleThreadDeamonPool("circuit-breaker", 1);
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        // todo: fill stress monitors through addAll

        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                pollStressMonitors();
            }
        });
    }

    private void pollStressMonitors() {
        while (!Thread.currentThread().isInterrupted()) {
            switch (tracer.getState()) {
                case RUNNING: {
                    for (StressMonitor stressMonitor : stressMonitors) {
                        if (stressMonitor.shouldPause()) {
                            tracer.pause();
                            break;
                        }
                    }
                    break;
                }
                case PAUSED: {
                    boolean shouldResume = true;
                    for (StressMonitor stressMonitor : stressMonitors) {
                        shouldResume &= stressMonitor.shouldResume();
                    }
                    if (shouldResume) {
                        tracer.resume();
                    }
                    break;
                }
            }
            try {
                // todo read poll interval from configuration
                int pollInterval = 1;
                if (logger.isTraceEnabled()) {
                    logger.trace("Scheduling next stress monitor polling in {}s", pollInterval);
                }
                TimeUnit.SECONDS.sleep(pollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    void registerStressMonitor(StressMonitor monitor) {
        stressMonitors.add(monitor);
    }

    void unregisterStressMonitor(StressMonitor monitor) {
        stressMonitors.remove(monitor);
    }

    @Override
    public void stop() throws Exception {
        this.threadPool.shutdownNow();
    }
}
