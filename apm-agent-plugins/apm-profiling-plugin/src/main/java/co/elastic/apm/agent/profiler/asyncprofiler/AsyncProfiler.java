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

import co.elastic.apm.agent.common.JvmRuntimeInfo;
import co.elastic.apm.agent.common.util.ResourceExtractionUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Java API for in-process profiling. Serves as a wrapper around
 * async-profiler native library. This class is a singleton.
 * The first call to {@link #getInstance(String, int)} initiates loading of
 * libasyncProfiler.so.
 * <p>
 * This is based on https://github.com/jvm-profiling-tools/async-profiler/blob/master/src/java/one/profiler/AsyncProfiler.java,
 * under Apache License 2.0.
 * It is modified to allow it to be shaded into the {@code co.elastic.apm} namespace
 * </p>
 */
public class AsyncProfiler {

    public static final String SAFEMODE_SYSTEM_PROPERTY_NAME = "AsyncProfiler.safemode";

    @Nullable
    private static volatile AsyncProfiler instance;

    private AsyncProfiler() {
    }

    public static AsyncProfiler getInstance(String profilerLibDirectory, int safemode) {
        AsyncProfiler result = AsyncProfiler.instance;
        if (result != null) {
            return result;
        }
        synchronized (AsyncProfiler.class) {
            if (instance == null) {
                if (JvmRuntimeInfo.ofCurrentVM().isJ9VM()) {
                    throw new IllegalStateException("OpenJ9 JVMs are not supported by async profiler. Please set " +
                        "profiling_inferred_spans_enabled to false");
                }
                try {
                    // set the AsyncProfiler.safemode system property with the configured safemode, so that optimizations
                    // can be applied already at load time. Specifically, if (safemode & 14) == 14 (2, 4 and 8 bits are set), then
                    // async profiler will avoid enabling CompiledMethodLoad events at load time, so to workaround a relatd JVM bug
                    // (https://bugs.openjdk.java.net/browse/JDK-8202883, https://bugs.openjdk.java.net/browse/JDK-8173361 and friends).
                    // safemode can still be set for each profiling session, but it can only be stricter than the safemode
                    // configured at load time.
                    System.setProperty(SAFEMODE_SYSTEM_PROPERTY_NAME, String.valueOf(safemode));
                    loadNativeLibrary(profilerLibDirectory);
                } catch (UnsatisfiedLinkError e) {
                    throw new IllegalStateException(String.format("It is likely that %s is not an executable location. Consider setting " +
                        "the profiling_inferred_spans_lib_directory property to a directory on a partition that allows execution",
                        profilerLibDirectory), e);
                }

                instance = new AsyncProfiler();
            }
            return instance;
        }
    }

    static void reset() {
        synchronized (AsyncProfiler.class) {
            instance = null;
        }
    }

    private static void loadNativeLibrary(String libraryDirectory) {
        String libraryName = getLibraryFileName();
        Path file = ResourceExtractionUtil.extractResourceToDirectory("asyncprofiler/" + libraryName + ".so", libraryName, ".so", Paths.get(libraryDirectory));
        System.load(file.toString());
    }

    static String getLibraryFileName() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        if (os.contains("linux")) {
            if (arch.contains("arm") || arch.contains("aarch32")) {
                return "libasyncProfiler-linux-arm";
            } else if (arch.contains("aarch")) {
                return "libasyncProfiler-linux-aarch64";
            } else if (arch.contains("64")) {
                return "libasyncProfiler-linux-x64";
            } else if (arch.contains("86")) {
                return "libasyncProfiler-linux-x86";
            } else {
                throw new IllegalStateException("Async-profiler does not work on Linux " + arch);
            }
        } else if (os.contains("mac")) {
            return "libasyncProfiler-macos-x64";
        } else {
            throw new IllegalStateException("Async-profiler does not work on " + os);
        }
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
     * Adds the given thread to the set of profiled threads
     *
     * @param thread A thread to add; null means current thread
     * @throws IllegalStateException If thread has not yet started or has already finished
     */
    public void enableProfilingThread(Thread thread) throws IllegalStateException {
        filterThread(thread, true);
    }

    /**
     * Removes the given thread to the set of profiled threads
     *
     * @param thread A thread to remove; null means current thread
     * @throws IllegalStateException If thread has not yet started or has already finished
     */
    public void disableProfilingThread(Thread thread) throws IllegalStateException {
        filterThread(thread, false);
    }

    /**
     * Adds the current thread to the set of profiled threads
     */
    public void enableProfilingCurrentThread() {
        filterThread0(null, true);
    }

    /**
     * Removes the current thread to the set of profiled threads
     */
    public void disableProfilingCurrentThread() throws IllegalStateException {
        filterThread0(null, false);
    }

    private void filterThread(Thread thread, boolean enable) throws IllegalStateException {
        synchronized (thread) {
            Thread.State state = thread.getState();
            if (state == Thread.State.NEW || state == Thread.State.TERMINATED) {
                return;
            }
            filterThread0(thread, enable);
        }
    }

    private native long getSamples();
    private native void start0(String event, long interval, boolean reset) throws IllegalStateException;
    private native void stop0() throws IllegalStateException;
    private native String execute0(String command) throws IllegalArgumentException, IOException;
    private native void filterThread0(Thread thread, boolean enable);

}
