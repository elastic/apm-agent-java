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
import java.util.List;

@VisibleForAdvice
public class ProcessHelper {

    private static final ProcessHelper INSTANCE = new ProcessHelper(DataStructures.<Process, Span>createWeakConcurrentMapWithCleanerThread());

    private final WeakConcurrentMap<Process, Span> inFlightSpans;

    ProcessHelper(WeakConcurrentMap<Process, Span> inFlightSpans) {
        this.inFlightSpans = inFlightSpans;
    }

    @VisibleForAdvice
    public static void startProcess(TraceContextHolder<?> transaction, Process process, List<String> command) {
        INSTANCE.doStartProcess(transaction, process, command.get(0));
    }

    @VisibleForAdvice
    public static void waitForEnd(@Nonnull Process process) {
        INSTANCE.doWaitForEnd(process);
    }

    void doStartProcess(@Nonnull TraceContextHolder<?> transaction, @Nonnull Process process, @Nonnull String processName) {
        if (inFlightSpans.containsKey(process)) {
            return;
        }
        Span span = transaction.createSpan()
            .withType("process")
            .withSubtype(processName)
            .withAction("execute")
            .withName(processName)
            .activate();

        inFlightSpans.putIfAbsent(process, span);
    }

    void doWaitForEnd(Process process) {

        // borrowed from java 8 Process#isAlive()
        boolean terminated;
        try {
            process.exitValue();
            terminated = true;
        } catch (IllegalThreadStateException e) {
            terminated = false;
        }

        if (terminated) {
            Span span = inFlightSpans.remove(process);
            if (span != null) {
                span.end();
            }
        }
    }
}
