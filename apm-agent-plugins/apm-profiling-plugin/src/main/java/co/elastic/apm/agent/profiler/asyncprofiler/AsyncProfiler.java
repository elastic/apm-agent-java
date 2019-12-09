package co.elastic.apm.agent.profiler.asyncprofiler;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static co.elastic.apm.agent.matcher.WildcardMatcher.caseSensitiveMatcher;

/**
 * Java API for in-process profiling. Serves as a wrapper around
 * async-profiler native library. This class is a singleton.
 * The first call to {@link #getInstance()} initiates loading of
 * libasyncProfiler.so.
 */
public abstract class AsyncProfiler {

    public static void main(String[] args) throws Exception {
        AsyncProfiler asyncProfiler = AsyncProfiler.getInstance("/Users/felixbarnsteiner/projects/github/elastic/apm-agent-java/apm-agent-plugins/apm-profiling-plugin/src/main/resources/libasyncProfiler.so");

        File file = new File(System.getProperty("java.io.tmpdir") + "/traces" + System.currentTimeMillis() + ".txt");
        try {
            System.out.println(asyncProfiler.execute("start,event=wall,interval=10000000,threads,alluser"));
            a();
            System.out.println(asyncProfiler.execute("stop,traces,ann,file=" + file));
            AsyncProfilerParser parser = new AsyncProfilerParser(file, List.of(caseSensitiveMatcher("co.elastic.apm.agent.profiler.asyncprofiler.*")), List.of(caseSensitiveMatcher("jdk.internal.*"), caseSensitiveMatcher("com.sun.*")));
            System.out.println(Files.readString(file.toPath()));
            System.out.println();
            parser.parse((stackTraceElements, threadId, samples) -> {
                if (!stackTraceElements.isEmpty()) {
                    System.out.println("threadId = " + threadId);
                    System.out.println("samples = " + samples);
                    System.out.println("stackTraceElements = " + stackTraceElements);
                    System.out.println();
                }
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
        return getInstance(null);
    }

    public static synchronized AsyncProfiler getInstance(@Nullable String libPath) {
        if (instance != null) {
            return instance;
        }

        if (libPath == null) {
            System.loadLibrary("asyncProfiler");
        } else {
            System.load(libPath);
        }

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

    public abstract void start0(String event, long interval, boolean reset) throws IllegalStateException;
    public abstract void stop0() throws IllegalStateException;
    public abstract String execute0(String command) throws IllegalArgumentException, java.io.IOException;
    public abstract String dumpCollapsed0(int counter);
    public abstract String dumpTraces0(int maxTraces);
    public abstract String dumpFlat0(int maxMethods);
    public abstract String version0();

    public static class DirectNativeBinding extends AsyncProfiler {

        public native void start0(String event, long interval, boolean reset) throws IllegalStateException;
        public native void stop0() throws IllegalStateException;
        public native String execute0(String command) throws IllegalArgumentException, java.io.IOException;
        public native String dumpCollapsed0(int counter);
        public native String dumpTraces0(int maxTraces);
        public native String dumpFlat0(int maxMethods);
        public native String version0();
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
