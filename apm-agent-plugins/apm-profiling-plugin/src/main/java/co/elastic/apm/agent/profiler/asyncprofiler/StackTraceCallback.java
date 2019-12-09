package co.elastic.apm.agent.profiler.asyncprofiler;

import java.util.List;

public interface StackTraceCallback {

    /**
     * IMPORTANT: the stackTraceElement list is cleared after invoking the callback
     *
     * @param stackTraceElements the call stack elements
     * @param threadId           the {@link Thread#getId()} of this thread
     * @param samples            the number of consecutive samples with the same stack trace
     */
    void onCallTree(List<String> stackTraceElements, long threadId, int samples);
}
