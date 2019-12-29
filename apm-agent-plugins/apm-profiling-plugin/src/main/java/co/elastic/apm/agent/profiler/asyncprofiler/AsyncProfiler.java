package co.elastic.apm.agent.profiler.asyncprofiler;

import co.elastic.apm.agent.impl.transaction.StackFrame;
import co.elastic.apm.agent.util.IOUtils;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Java API for in-process profiling. Serves as a wrapper around
 * async-profiler native library. This class is a singleton.
 * The first call to {@link #getInstance()} initiates loading of
 * libasyncProfiler.so.
 */
public abstract class AsyncProfiler {

    public static void main(String[] args) throws Exception {
        AsyncProfiler asyncProfiler = AsyncProfiler.getInstance();

        File file = new File(System.getProperty("java.io.tmpdir") + "/traces" + System.currentTimeMillis() + ".jfr");
        try {
            System.out.println(asyncProfiler.execute("start,jfr,event=wall,interval=10000000,alluser,file=" + file));
            a();
            System.out.println(asyncProfiler.execute("stop"));
            JfrParser jfrParser = new JfrParser(file);
            jfrParser.parse((threadId, stackTraceId, nanoTime) -> {
                List<StackFrame> stackFrames = new ArrayList<>();
                jfrParser.getStackTrace(stackTraceId, false, stackFrames);
                if (!stackFrames.isEmpty()) {
                    System.out.println(stackFrames);
                }
                stackFrames.clear();
            });

        } finally {
            if (!file.delete()) {
                file.deleteOnExit();
            }
        }
    }

    private static void a() throws InterruptedException {
        Thread.sleep(250);
        b();
        Thread.sleep(250);
        b();
    }

    private static void b() throws InterruptedException {
        Thread.sleep(250);
    }

    @Nullable
    private static AsyncProfiler instance;

    private final String version;

    public AsyncProfiler() {
        this.version = version0();
    }

    // TODO export libasyncProfiler.so to temp directory, based on current OS (reuse if unchanged)
    public static AsyncProfiler getInstance() {
        if (instance != null) {
            return instance;
        }
        File file = IOUtils.exportResourceToTemp("libasyncProfiler.so");
        System.load(file.getAbsolutePath());

        instance = newInstance();
        return instance;
    }

    /*
     * Allows AsyncProfiler to be shaded. JNI mapping works for a specific package so shading normally doesn't work.
     */
    private static AsyncProfiler newInstance() {
        try {
            return new ByteBuddy()
                .redefine(DirectNativeBinding.class)
                .name("one.profiler.AsyncProfiler")
                .make()
                .load(AsyncProfiler.class.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                .getLoaded()
                .getConstructor()
                .newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Start profiling
     *
     * @param event Profiling event, see {@link Events}
     * @param interval Sampling interval, e.g. nanoseconds for Events.CPU
     * @throws IllegalStateException If profiler is already running
     */
    public void start(String event, long interval) throws IllegalStateException {
        start0(event, interval, true);
    }

    /**
     * Start or resume profiling without resetting collected data.
     * Note that event and interval may change since the previous profiling session.
     *
     * @param event Profiling event, see {@link Events}
     * @param interval Sampling interval, e.g. nanoseconds for Events.CPU
     * @throws IllegalStateException If profiler is already running
     */
    public void resume(String event, long interval) throws IllegalStateException {
        start0(event, interval, false);
    }

    /**
     * Stop profiling (without dumping results)
     *
     * @throws IllegalStateException If profiler is not running
     */
    public void stop() throws IllegalStateException {
        stop0();
    }

    /**
     * Get the number of samples collected during the profiling session
     *
     * @return Number of samples
     */
    public native long getSamples();

    /**
     * Get profiler agent version, e.g. "1.0"
     *
     * @return Version string
     */
    public String getVersion() {
        return version;
    }

    /**
     * Execute an agent-compatible profiling command -
     * the comma-separated list of arguments described in arguments.cpp
     *
     * @param command Profiling command
     * @return The command result
     * @throws IllegalArgumentException If failed to parse the command
     * @throws java.io.IOException If failed to create output file
     */
    public String execute(String command) throws IllegalArgumentException, java.io.IOException {
        return execute0(command);
    }

    /**
     * Dump profile in 'collapsed stacktraces' format
     *
     * @param counter Which counter to display in the output
     * @return Textual representation of the profile
     */
    public String dumpCollapsed(Counter counter) {
        return dumpCollapsed0(counter.ordinal());
    }

    /**
     * Dump collected stack traces
     *
     * @param maxTraces Maximum number of stack traces to dump. 0 means no limit
     * @return Textual representation of the profile
     */
    public String dumpTraces(int maxTraces) {
        return dumpTraces0(maxTraces);
    }

    /**
     * Dump flat profile, i.e. the histogram of the hottest methods
     *
     * @param maxMethods Maximum number of methods to dump. 0 means no limit
     * @return Textual representation of the profile
     */
    public String dumpFlat(int maxMethods) {
        return dumpFlat0(maxMethods);
    }

    /**
     * Get OS thread ID of the given Java thread. On Linux, this is the same number
     * as gettid() returns. The result ID matches 'tid' in the profiler output.
     *
     * @param thread Java thread object
     * @return Positive number that matches native (OS level) thread ID,
     * or -1 if the given thread has not yet started or has already finished
     */
    public long getNativeThreadId(Thread thread) {
        synchronized (thread) {
            Thread.State state = thread.getState();
            if (state != Thread.State.NEW && state != Thread.State.TERMINATED) {
                return getNativeThreadId0(thread);
            }
        }
        return -1;
    }

    public abstract void start0(String event, long interval, boolean reset) throws IllegalStateException;
    public abstract void stop0() throws IllegalStateException;
    public abstract String execute0(String command) throws IllegalArgumentException, java.io.IOException;
    public abstract String dumpCollapsed0(int counter);
    public abstract String dumpTraces0(int maxTraces);
    public abstract String dumpFlat0(int maxMethods);
    public abstract String version0();
    public abstract long getNativeThreadId0(Thread thread);

    public static class DirectNativeBinding extends AsyncProfiler {

        public native void start0(String event, long interval, boolean reset) throws IllegalStateException;
        public native void stop0() throws IllegalStateException;
        public native String execute0(String command) throws IllegalArgumentException, java.io.IOException;
        public native String dumpCollapsed0(int counter);
        public native String dumpTraces0(int maxTraces);
        public native String dumpFlat0(int maxMethods);
        public native String version0();
        public native long getNativeThreadId0(Thread thread);
    }

    /**
     * Which metrics to use when generating profile in collapsed stack traces format.
     */
    public enum Counter {
        SAMPLES,
        TOTAL
    }

    /**
     * Predefined event names to use in {@link AsyncProfiler#start(String, long)}
     */
    public class Events {
        public static final String CPU    = "cpu";
        public static final String ALLOC  = "alloc";
        public static final String LOCK   = "lock";
        public static final String WALL   = "wall";
        public static final String ITIMER = "itimer";
    }
}
