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
package co.elastic.apm.agent.util;

import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.AbstractLifecycleListener;
import co.elastic.apm.agent.tracer.Tracer;

import javax.annotation.Nullable;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadDump extends AbstractLifecycleListener {

    private Logger log = LoggerFactory.getLogger(ThreadDump.class);

    @Nullable
    private ScheduledThreadPoolExecutor executor;

    @Override
    public void start(Tracer tracer) throws Exception {

        long threadDumpInterval = tracer.getConfig(CoreConfigurationImpl.class).getThreadDumpInterval();
        if (threadDumpInterval <= 0) {
            return;
        }

        if (!log.isDebugEnabled()) {
            log.error("thread dump option requires debug log level");
            return;
        }

        if (threadDumpInterval < 100) {
            log.error("thread dump frequency too high, adjusted to every 100ms");
            threadDumpInterval = 100;
        }

        log.warn("thread dump will be generated every %s ms", threadDumpInterval);

        executor = ExecutorUtils.createSingleThreadSchedulingDaemonPool("thread-dump");

        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
                StringBuilder sb = new StringBuilder();
                for (ThreadInfo threadInfo : threadMXBean.dumpAllThreads(true, true)) {
                    sb.append(threadInfo.toString());
                }

                log.debug("thread dump: \n\n {}", sb);

            }
        }, threadDumpInterval, threadDumpInterval, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() throws Exception {
        if (executor == null) {
            return;
        }
        ExecutorUtils.shutdownAndWaitTermination(executor);
    }
}
