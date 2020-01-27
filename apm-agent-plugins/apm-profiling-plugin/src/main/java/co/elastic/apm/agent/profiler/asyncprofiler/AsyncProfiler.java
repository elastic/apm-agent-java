/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
/*
 * Copyright 2018 Andrei Pangin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package co.elastic.apm.agent.profiler.asyncprofiler;

import co.elastic.apm.agent.util.IOUtils;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;

import javax.annotation.Nullable;
import java.io.File;

/**
 * Java API for in-process profiling. Serves as a wrapper around
 * async-profiler native library. This class is a singleton.
 * The first call to {@link #getInstance()} initiates loading of
 * libasyncProfiler.so.
 * <p>
 * This is based on https://github.com/jvm-profiling-tools/async-profiler/blob/master/src/java/one/profiler/AsyncProfiler.java,
 * under Apache License 2.0.
 * It is modified to allow it to be shaded into the {@code co.elastic.apm} namespace
 * </p>
 */
public abstract class AsyncProfiler {

    @Nullable
    private static volatile AsyncProfiler instance;

    private final String version;

    public AsyncProfiler() {
        this.version = version0();
    }

    public static AsyncProfiler getInstance() {
        AsyncProfiler result = AsyncProfiler.instance;
        if (result != null) {
            return result;
        }
        synchronized (AsyncProfiler.class) {
            if (instance == null) {
                instance = newInstance();
            }
            return instance;
        }
    }

    /*
     * Allows AsyncProfiler to be shaded. JNI mapping works for a specific package so shading normally doesn't work.
     */
    private static AsyncProfiler newInstance() {
        try {
            return new ByteBuddy()
                // ClassFileLocator.ForClassLoader.ofBootLoader() can't resolve resources added via Instrumentation.appendToBootstrapClassLoaderSearch
                // see also https://stackoverflow.com/questions/51347432/why-cant-i-load-resources-which-are-appended-to-the-bootstrap-class-loader-sear
                .redefine(DirectNativeBinding.class, ClassFileLocator.ForClassLoader.ofSystemLoader())
                .name("one.profiler.AsyncProfiler")
                .make()
                .load(AsyncProfiler.class.getClassLoader(), ClassLoadingStrategy.Default.CHILD_FIRST)
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
     * Get OS thread ID of the current Java thread. On Linux, this is the same number
     * as gettid() returns. The result ID matches 'tid' in the profiler output.
     *
     * @return 64-bit integer that matches native (OS level) thread ID
     */
    public long getNativeThreadId() {
        return getNativeThreadId0();
    }

    public abstract void start0(String event, long interval, boolean reset) throws IllegalStateException;
    public abstract void stop0() throws IllegalStateException;
    public abstract String execute0(String command) throws IllegalArgumentException, java.io.IOException;
    public abstract String dumpCollapsed0(int counter);
    public abstract String dumpTraces0(int maxTraces);
    public abstract String dumpFlat0(int maxMethods);
    public abstract String version0();
    public abstract long getNativeThreadId0();

    /**
     * Inspired by https://gist.github.com/raphw/be0994259e75652f057c9e1d3ee5f567
     */
    public static class DirectNativeBinding extends AsyncProfiler {

        static {
            loadNativeLibrary();
        }

        private static void loadNativeLibrary() {
            String libraryName = getLibraryFileName();
            File file = IOUtils.exportResourceToTemp("asyncprofiler/" + libraryName + ".so", libraryName, ".so");
            System.load(file.getAbsolutePath());
        }

        private static String getLibraryFileName() {
            String os = System.getProperty("os.name").toLowerCase();
            String arch = System.getProperty("os.arch").toLowerCase();
            if (os.contains("linux")) {
                if (arch.contains("arm") || arch.contains("aarch")) {
                    return "libasyncProfiler-linux-arm";
                } else if (arch.contains("64")) {
                    return "libasyncProfiler-linux-x64";
                } else if (arch.contains("86")) {
                    return "libasyncProfiler-linux-x86";
                } else {
                    throw new IllegalStateException("Async-profiler does not work on Linux " + arch);
                }
            } else if (os.contains("mac")) {
                return "libasyncProfiler-macos";
            } else {
                throw new IllegalStateException("Async-profiler does not work on " + os);
            }
        }

        public native void start0(String event, long interval, boolean reset) throws IllegalStateException;
        public native void stop0() throws IllegalStateException;
        public native String execute0(String command) throws IllegalArgumentException, java.io.IOException;
        public native String dumpCollapsed0(int counter);
        public native String dumpTraces0(int maxTraces);
        public native String dumpFlat0(int maxMethods);
        public native String version0();
        public native long getNativeThreadId0();
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
    public static class Events {
        public static final String CPU    = "cpu";
        public static final String ALLOC  = "alloc";
        public static final String LOCK   = "lock";
        public static final String WALL   = "wall";
        public static final String ITIMER = "itimer";
    }
}
