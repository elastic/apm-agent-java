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
package co.elastic.apm.agent.process;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.util.DataStructures;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.List;

@VisibleForAdvice
public class ProcessHelper {

    private static final ProcessHelper INSTANCE = new ProcessHelper(new WeakConcurrentMap.WithInlinedExpunction<>());

    private final WeakConcurrentMap<Process, Span> inFlightSpans;

    ProcessHelper(WeakConcurrentMap<Process, Span> inFlightSpans) {
        this.inFlightSpans = inFlightSpans;
    }

    @VisibleForAdvice
    public static void startProcess(TraceContextHolder<?> parentContext, Process process, List<String> command) {
        INSTANCE.doStartProcess(parentContext, process, command.get(0));
    }

    @VisibleForAdvice
    public static void endProcess(@Nonnull Process process, boolean checkTerminatedProcess) {
        INSTANCE.doEndProcess(process, checkTerminatedProcess);
    }

    /**
     * Starts process span
     *
     * @param parentContext parent context
     * @param process       started process
     * @param processName   process name
     */
    void doStartProcess(@Nonnull TraceContextHolder<?> parentContext, @Nonnull Process process, @Nonnull String processName) {
        if (inFlightSpans.containsKey(process)) {
            return;
        }

        String binaryName = getBinaryName(processName);

        Span span = parentContext.createSpan()
            .withType("process")
            .withSubtype(binaryName)
            .withAction("execute")
            .withName(binaryName);

        // We don't require span to be activated as the background process is not really linked to current thread
        // and there won't be any child span linked to process span

        inFlightSpans.put(process, span);
    }

    private static String getBinaryName(String processName) {
        int lastSeparator = processName.lastIndexOf(File.separatorChar);
        return lastSeparator < 0 ? processName : processName.substring(lastSeparator + 1);
    }

    /**
     * Ends process span
     *
     * @param process                process that is being terminated
     * @param checkTerminatedProcess if {@code true}, will only terminate span if process is actually terminated, will
     *                               unconditionally terminate process span otherwise
     */
    void doEndProcess(Process process, boolean checkTerminatedProcess) {

        // borrowed from java 8 Process#isAlive()
        // it has the same caveat as isAlive, which means that it will not detect process termination
        // until the actual process has terminated, for example right after a call to Process#destroy().
        // in that case, ignoring the process actual status is relevant.
        boolean terminated = !checkTerminatedProcess;
        if (checkTerminatedProcess) {
            try {
                process.exitValue();
                terminated = true;
            } catch (IllegalThreadStateException e) {
                terminated = false;
            }
        }

        if (terminated) {
            Span span = inFlightSpans.remove(process);
            if (span != null) {
                span.end();
            }
        }
    }
}
