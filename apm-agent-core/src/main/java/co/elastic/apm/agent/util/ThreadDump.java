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
import java.lang.management.MonitorInfo;
import java.lang.management.LockInfo;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadDump extends AbstractLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(ThreadDump.class);

    private static final int MAX_FRAMES = 40;

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
                    sb.append(ThreadDump.toString(threadInfo));
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

    /**
     * Copy of {@link ThreadInfo#toString()} with a higher frame size as default implementation.
     *
     * @param threadInfo thread info
     * @return thread info as string
     */
    private static String toString(ThreadInfo threadInfo) {
        StringBuilder sb = new StringBuilder("\"" + threadInfo.getThreadName() + "\"" +
            // note: daemon and priority now available < java9
            " Id=" + threadInfo.getThreadId() + " " +
            threadInfo.getThreadState());
        if (threadInfo.getLockName() != null) {
            sb.append(" on " + threadInfo.getLockName());
        }
        if (threadInfo.getLockOwnerName() != null) {
            sb.append(" owned by \"" + threadInfo.getLockOwnerName() +
                "\" Id=" + threadInfo.getLockOwnerId());
        }
        if (threadInfo.isSuspended()) {
            sb.append(" (suspended)");
        }
        if (threadInfo.isInNative()) {
            sb.append(" (in native)");
        }
        sb.append('\n');

        StackTraceElement[] stackTrace = threadInfo.getStackTrace();
        int i = 0;
        for (; i < stackTrace.length && i < MAX_FRAMES; i++) {
            StackTraceElement ste = stackTrace[i];
            sb.append("\tat " + ste.toString());
            sb.append('\n');
            if (i == 0 && threadInfo.getLockInfo() != null) {
                Thread.State ts = threadInfo.getThreadState();
                switch (ts) {
                    case BLOCKED:
                        sb.append("\t-  blocked on " + threadInfo.getLockInfo());
                        sb.append('\n');
                        break;
                    case WAITING:
                        sb.append("\t-  waiting on " + threadInfo.getLockInfo());
                        sb.append('\n');
                        break;
                    case TIMED_WAITING:
                        sb.append("\t-  waiting on " + threadInfo.getLockInfo());
                        sb.append('\n');
                        break;
                    default:
                }
            }

            for (MonitorInfo mi : threadInfo.getLockedMonitors()) {
                if (mi.getLockedStackDepth() == i) {
                    sb.append("\t-  locked " + mi);
                    sb.append('\n');
                }
            }
        }
        if (i < stackTrace.length) {
            sb.append("\t...");
            sb.append('\n');
        }

        LockInfo[] locks = threadInfo.getLockedSynchronizers();
        if (locks.length > 0) {
            sb.append("\n\tNumber of locked synchronizers = " + locks.length);
            sb.append('\n');
            for (LockInfo li : locks) {
                sb.append("\t- " + li);
                sb.append('\n');
            }
        }
        sb.append('\n');
        return sb.toString();

    }


}
