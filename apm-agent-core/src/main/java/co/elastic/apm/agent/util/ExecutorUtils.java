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

import co.elastic.apm.agent.common.ThreadUtils;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

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
import java.util.concurrent.atomic.AtomicInteger;

public final class ExecutorUtils {

    private static final Logger logger = LoggerFactory.getLogger(ExecutorUtils.class);

    private ExecutorUtils() {
        // don't instantiate
    }

    public static ScheduledThreadPoolExecutor createSingleThreadSchedulingDaemonPool(final String threadPurpose) {
        final SingleNamedThreadFactory daemonThreadFactory = new SingleNamedThreadFactory(ThreadUtils.addElasticApmThreadPrefix(threadPurpose));
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
        final ThreadFactory daemonThreadFactory = new SingleNamedThreadFactory(threadName);
        return new SingleNamedDaemonThreadPoolExecutor(queueCapacity, daemonThreadFactory, threadName);
    }

    public static ThreadPoolExecutor createThreadDaemonPool(final String threadPurpose, int poolSize, int queueCapacity) {
        final ThreadFactory daemonThreadFactory = new NamedThreadFactory(threadPurpose);
        return new NamedDaemonThreadPoolExecutor(poolSize, queueCapacity, daemonThreadFactory, threadPurpose);
    }

    public static class SingleNamedThreadFactory implements ThreadFactory {
        private final String threadName;

        public SingleNamedThreadFactory(String threadName) {
            this.threadName = threadName;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName(threadName);
            ClassLoader originalContextCL = thread.getContextClassLoader();
            thread.setContextClassLoader(null);
            logThreadCreation(originalContextCL, threadName);
            return thread;
        }
    }

    static void logThreadCreation(ClassLoader originalContextCL, String threadName) {
        if (logger.isDebugEnabled()) {
            logger.debug("A new thread named `{}` was created. The original context class loader of this thread ({}) has been overridden",
                    threadName, originalContextCL);
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Stack trace related to thread creation: ", new Throwable());
        }
    }

    public static class NamedThreadFactory implements ThreadFactory {
        private final String threadPurpose;
        private final AtomicInteger threadCounter;

        public NamedThreadFactory(String threadPurpose) {
            this.threadPurpose = threadPurpose;
            threadCounter = new AtomicInteger();
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            String threadName = ThreadUtils.addElasticApmThreadPrefix(threadPurpose) + "-" + threadCounter.getAndIncrement();
            thread.setName(threadName);
            ClassLoader originalContextCL = thread.getContextClassLoader();
            thread.setContextClassLoader(null);
            logThreadCreation(originalContextCL, threadName);
            return thread;
        }
    }

    private static class SingleNamedDaemonThreadPoolExecutor extends ThreadPoolExecutor {
        private final String threadName;

        SingleNamedDaemonThreadPoolExecutor(int queueCapacity, ThreadFactory daemonThreadFactory, String threadName) {
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

    private static class NamedDaemonThreadPoolExecutor extends ThreadPoolExecutor {
        private final String threadPrefix;

        NamedDaemonThreadPoolExecutor(int poolSize, int queueCapacity, ThreadFactory daemonThreadFactory, String threadPrefix) {
            super(poolSize, poolSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(queueCapacity), daemonThreadFactory);
            this.threadPrefix = threadPrefix;
        }

        @Override
        public String toString() {
            return super.toString() + "(threads name prefix = " + threadPrefix + ")";
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

    /**
     * Implementation adapted form the {@link ExecutorService} Javadoc
     */
    public static void shutdownAndWaitTermination(ExecutorService executor) {
        shutdownAndWaitTermination(executor, 1, TimeUnit.SECONDS);
    }

    public static void shutdownAndWaitTermination(ExecutorService executor, long timeout, TimeUnit unit){
        // Disable new tasks from being submitted
        executor.shutdown();
        try {
            // Wait a while for existing tasks to terminate
            if (!executor.awaitTermination(timeout, unit)) {
                // Cancel currently executing tasks
                executor.shutdownNow();
                // Wait a while for tasks to respond to being cancelled
                if (!executor.awaitTermination(timeout, unit)) {
                    logger.warn("Thread pool did not terminate in time " + executor);
                }
            }
        } catch (InterruptedException e) {
            // (Re-)Cancel if current thread also interrupted
            executor.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }
}
