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
package co.elastic.apm.agent.collections;

import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.util.ExecutorUtils;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Regularly calls {@link WeakConcurrentProviderImpl#expungeStaleEntries()}
 */
public class WeakMapCleaner extends AbstractLifecycleListener implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(WeakMapCleaner.class);

    private final ScheduledThreadPoolExecutor scheduler;

    public WeakMapCleaner() {
        this.scheduler = ExecutorUtils.createSingleThreadSchedulingDaemonPool("weak-map-cleaner");
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        scheduler.scheduleWithFixedDelay(this, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public void stop() throws Exception {
        scheduler.shutdownNow();
        scheduler.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        try {
            WeakConcurrentProviderImpl.expungeStaleEntries();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }
}
