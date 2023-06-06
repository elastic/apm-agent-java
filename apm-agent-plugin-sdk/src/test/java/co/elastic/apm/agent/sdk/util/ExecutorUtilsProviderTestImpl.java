package co.elastic.apm.agent.sdk.util;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static co.elastic.apm.agent.sdk.util.ExecutorUtils.ExecutorUtilsProvider;

public class ExecutorUtilsProviderTestImpl implements ExecutorUtilsProvider {

    @Override
    public boolean isAgentExecutor(Executor executor) {
        return executor instanceof SimpleScheduledThreadPoolExecutor;
    }

    @Override
    public ScheduledExecutorService createSingleThreadSchedulingDaemonPool(String threadPurpose) {
        return new SimpleScheduledThreadPoolExecutor();
    }

    @Override
    public void shutdownAndWaitTermination(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException();
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static class SimpleScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {

        private SimpleScheduledThreadPoolExecutor() {
            super(1);
        }
    }
}
