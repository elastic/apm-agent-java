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
package co.elastic.apm.agent.process;

import co.elastic.apm.agent.sdk.state.GlobalVariables;
import co.elastic.apm.agent.tracer.ElasticContext;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.reference.ReferenceCountedMap;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Having direct references to {@link Process} class is safe here because those are loaded in the bootstrap classloader.
 * Thus, there is no need to separate helper interface from implementation.
 */
class ProcessHelper {

    private static final ProcessHelper INSTANCE = new ProcessHelper(GlobalTracer.get().<Process, Span<?>>newReferenceCountedMap());

    /**
     * A thread local used to indicate whether the currently invoked instrumented method is invoked by the plugin itself.
     * More concretely - some span termination routes attempt to invoke {@link Process#exitValue()}, which is itself
     * instrumented in order to detect process termination. When this method is invoked by the plugin itself, we want to
     * avoid applying its instrumentation logic.
     */
    private static final ThreadLocal<Boolean> inTracingContext = GlobalVariables.get(ProcessHelper.class, "inTracingContext", new ThreadLocal<Boolean>());

    private final ReferenceCountedMap<Process, Span<?>> inFlightSpans;

    ProcessHelper(ReferenceCountedMap<Process, Span<?>> inFlightSpans) {
        this.inFlightSpans = inFlightSpans;
    }

    public static boolean isTracingOnCurrentThread() {
        return inTracingContext.get() == Boolean.TRUE;
    }

    static void startProcess(ElasticContext<?> activeContext, Process process, List<String> command) {
        INSTANCE.doStartProcess(activeContext, process, command.get(0));
    }

    static void endProcess(@Nonnull Process process, boolean checkTerminatedProcess) {
        INSTANCE.doEndProcess(process, checkTerminatedProcess);
    }

    static void endProcessSpan(@Nonnull Process process, int exitValue) {
        INSTANCE.doEndProcessSpan(process, exitValue);
    }

    /**
     * Starts process span
     *
     * @param activeContext parent context
     * @param process       started process
     * @param processName   process name
     */
    void doStartProcess(ElasticContext<?> activeContext, @Nonnull Process process, @Nonnull String processName) {
        if (inFlightSpans.contains(process)) {
            return;
        }
        Span<?> span = activeContext.createSpan();
        if (span == null) {
            return;
        }

        String binaryName = getBinaryName(processName);

        span.withType("process")
            .withName(binaryName);

        // We don't require span to be activated as the background process is not really linked to current thread
        // and there won't be any child span linked to process span

        inFlightSpans.put(process, span);
    }

    private static String getBinaryName(String processName) {
        int lastSeparator = processName.lastIndexOf(System.getProperty("file.separator"));
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

        Span<?> span = inFlightSpans.get(process);
        if (span == null) {
            return;
        }

        Outcome outcome = Outcome.UNKNOWN;
        boolean endAndRemoveSpan = !checkTerminatedProcess;
        if (checkTerminatedProcess) {
            // borrowed from java 8 Process#isAlive()
            // it has the same caveat as isAlive, which means that it will not detect process termination
            // until the actual process has terminated, for example right after a call to Process#destroy().
            // in that case, ignoring the process actual status is relevant.
            try {
                inTracingContext.set(Boolean.TRUE);
                int exitValue = process.exitValue();
                outcome = exitValue == 0 ? Outcome.SUCCESS : Outcome.FAILURE;
                endAndRemoveSpan = true;
            } catch (IllegalThreadStateException e) {
                // process hasn't terminated, we don't know it's actual return value
                outcome = Outcome.UNKNOWN;
                endAndRemoveSpan = false;
            } finally {
                inTracingContext.remove();
            }
        }

        if (endAndRemoveSpan) {
            removeAndEndSpan(process, outcome);
        }
    }

    /**
     * Can be used to end the span corresponding the provided {@link Process} when the exit value is already known
     * @param process       process that is being terminated
     * @param exitValue     exit value of the terminated process
     */
    void doEndProcessSpan(Process process, int exitValue) {
        removeAndEndSpan(process, exitValue == 0 ? Outcome.SUCCESS : Outcome.FAILURE);
    }

    private void removeAndEndSpan(Process process, Outcome outcome) {
        Span<?> span = inFlightSpans.remove(process);
        if (span != null) {
            span.withOutcome(outcome).
                end();
        }
    }
}
