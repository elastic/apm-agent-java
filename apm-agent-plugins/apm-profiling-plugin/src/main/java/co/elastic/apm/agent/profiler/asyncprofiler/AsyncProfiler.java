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

    public AsyncProfiler() {
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

    public abstract void stop0() throws IllegalStateException;
    public abstract String execute0(String command) throws IllegalArgumentException, java.io.IOException;
    public abstract void filterThread0(Thread thread, boolean enable);

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

        public native void stop0() throws IllegalStateException;
        public native String execute0(String command) throws IllegalArgumentException, java.io.IOException;
        public native void filterThread0(Thread thread, boolean enable);
    }
}
