package co.elastic.apm.agent.util;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static co.elastic.apm.agent.sdk.util.ExecutorUtils.*;

public class ExecutorUtilsProviderImpl implements ExecutorUtilsProvider {

    @Override
    public boolean isAgentExecutor(Executor executor) {
        return ExecutorUtils.isAgentExecutor(executor);
    }

    @Override
    public ScheduledExecutorService createSingleThreadSchedulingDaemonPool(String threadPurpose) {
        return ExecutorUtils.createSingleThreadSchedulingDaemonPool(threadPurpose);
    }

    @Override
    public void shutdownAndWaitTermination(ExecutorService executor) {
        ExecutorUtils.shutdownAndWaitTermination(executor);
    }
}
