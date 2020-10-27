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
package co.elastic.apm.agent.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class ExecutorUtils {

    private static final Logger logger = LoggerFactory.getLogger(ExecutorUtils.class);

    private ExecutorUtils() {
        // don't instantiate
    }

    public static ScheduledThreadPoolExecutor createSingleThreadSchedulingDaemonPool(final String threadPurpose) {
        final NamedThreadFactory daemonThreadFactory = new NamedThreadFactory(ThreadUtils.addElasticApmThreadPrefix(threadPurpose));
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, daemonThreadFactory) {
            @Override
            public String toString() {
                return super.toString() + "(thread name = " + daemonThreadFactory.threadName + ")";
            }

            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                super.afterExecute(r, t);
                logException(r, t);
            }
        };
        executor.setMaximumPoolSize(1);
        return executor;
    }

    public static ThreadPoolExecutor createSingleThreadDaemonPool(final String threadPurpose, int queueCapacity) {
        String threadName = ThreadUtils.addElasticApmThreadPrefix(threadPurpose);
        final ThreadFactory daemonThreadFactory = new NamedThreadFactory(threadName);
        return new NamedDaemonThreadPoolExecutor(queueCapacity, daemonThreadFactory, threadName);
    }

    public static class NamedThreadFactory implements ThreadFactory {
        private final String threadName;

        public NamedThreadFactory(String threadName) {
            this.threadName = threadName;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName(threadName);
            thread.setContextClassLoader(null);
            return thread;
        }
    }

    private static class NamedDaemonThreadPoolExecutor extends ThreadPoolExecutor {
        private final String threadName;

        NamedDaemonThreadPoolExecutor(int queueCapacity, ThreadFactory daemonThreadFactory, String threadName) {
            super(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(queueCapacity), daemonThreadFactory);
            this.threadName = threadName;
        }

        @Override
        public String toString() {
            return super.toString() + "(thread name = " + threadName + ")";
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            logException(r, t);
        }
    }

    /**
     * Overriding this method makes sure that exceptions thrown by a task are not silently swallowed.
     *
     * @see ThreadPoolExecutor#afterExecute(Runnable, Throwable)
     */
    private static void logException(Runnable r, @Nullable Throwable t) {
        if (t == null && r instanceof Future<?>) {
            try {
                Future<?> future = (Future<?>) r;
                if (future.isDone()) {
                    future.get();
                }
            } catch (CancellationException ce) {
                t = ce;
            } catch (ExecutionException ee) {
                t = ee.getCause();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt(); // ignore/reset
            }
        }
        if (t != null) {
            logger.error(t.getMessage(), t);
        }
    }

    public static void shutdown(ExecutorService executor) {
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.warn("executor service shutdown has been interrupted", e);
            executor.shutdownNow();
        }
    }
}
