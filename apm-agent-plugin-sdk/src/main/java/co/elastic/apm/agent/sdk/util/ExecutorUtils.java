package co.elastic.apm.agent.sdk.util;

import javax.annotation.Nullable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public abstract class ExecutorUtils {

    @Nullable
    private static ExecutorUtils instance;

    public static void init(ExecutorUtils instance) {
        ExecutorUtils.instance = instance;
    }

    public static ScheduledExecutorService createSingleThreadSchedulingDaemonPool(final String threadPurpose) {
        ExecutorUtils instance = ExecutorUtils.instance;
        if (instance == null) {
            return new NamedScheduledThreadPoolExecutor(threadPurpose);
        } else {
            return instance.doCreateSingleThreadSchedulingDaemonPool(threadPurpose);
        }
    }


    protected abstract ScheduledExecutorService doCreateSingleThreadSchedulingDaemonPool(final String threadPurpose);

    public static boolean isAgentExecutor(Executor executor) {
        ExecutorUtils instance = ExecutorUtils.instance;
        if (instance == null) {
            return executor instanceof NamedScheduledThreadPoolExecutor;
        } else {
            return instance.doIsAgentExecutor(executor);
        }
    }

    protected abstract boolean doIsAgentExecutor(Executor executor);

    public static void shutdownAndWaitTermination(ExecutorService executor) {
        ExecutorUtils instance = ExecutorUtils.instance;
        if (instance == null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                        throw new IllegalStateException();
                    }
                }
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        } else {
            instance.doShutdownAndWaitTermination(executor);
        }
    }

    protected abstract void doShutdownAndWaitTermination(ExecutorService executor);

    private static class NamedScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {

        private final String threadPurpose;

        public NamedScheduledThreadPoolExecutor(String threadPurpose) {
            super(1, new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("executor-util-" + threadPurpose);
                    thread.setDaemon(true);
                    return thread;
                }
            });
            this.threadPurpose = threadPurpose;
        }

        @Override
        public String toString() {
            return super.toString() + "(thread name = " + threadPurpose + ")";
        }
    }
}
