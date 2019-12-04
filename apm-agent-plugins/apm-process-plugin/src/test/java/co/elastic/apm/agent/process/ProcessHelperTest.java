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

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.TransactionUtils;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.util.DataStructures;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessHelperTest extends AbstractInstrumentationTest {

    // implementation note:
    //
    // Testing instrumentation of classes loaded by bootstrap classloader can't be done with regular unit tests
    // as the agent is loaded in the application/system classloader when they are run.
    //
    // Hence, in order to maximize test coverage we thoroughly test helper implementation where most of the actual code
    // of this instrumentation is. Also, integration test cover this feature for the general case with a packaged
    // agent and thus they don't have such limitation

    @Nullable
    private Transaction transaction = null;

    private WeakConcurrentMap<Process, Span> storageMap;
    private ProcessHelper helper;

    @BeforeEach
    void before() {
        transaction = new Transaction(tracer);
        TransactionUtils.fillTransaction(transaction);

        storageMap = DataStructures.createWeakConcurrentMapWithCleanerThread();
        helper = new ProcessHelper(storageMap);
    }

    @Test
    void checkSpanNaming() {
        Process process = mock(Process.class);

        String binaryName = "hello";
        String programName = Paths.get("bin", binaryName).toAbsolutePath().toString();

        helper.doStartProcess(transaction, process, programName);

        helper.doWaitForEnd(process);

        assertThat(reporter.getSpans()).hasSize(1);
        Span span = reporter.getSpans().get(0);

        assertThat(span.getNameAsString()).isEqualTo(binaryName);
        assertThat(span.getType()).isEqualTo("process");
        assertThat(span.getSubtype()).isEqualTo(binaryName);
        assertThat(span.getAction()).isEqualTo("execute");
    }

    @Test
    void startTwiceShouldIgnore() {
        Process process = mock(Process.class);

        helper.doStartProcess(transaction, process, "hello");
        Span span = storageMap.get(process);

        helper.doStartProcess(transaction, process, "hello");
        assertThat(storageMap.get(process))
            .describedAs("initial span should not be overwritten")
            .isSameAs(span);
    }

    @Test
    void endTwiceShouldIgnore() {
        Process process = mock(Process.class);

        helper.doStartProcess(transaction, process, "hello");
        assertThat(storageMap).isNotEmpty();

        helper.doWaitForEnd(process);

        // this second call should be ignored, thus exception not reported
        helper.doWaitForEnd(process);

        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getErrors())
            .describedAs("error should not be reported")
            .isEmpty();
    }

    @Test
    void createMultipleProcessInTransaction() {
        Process process = mock(Process.class);

        helper.doStartProcess(transaction, process, "hello");
        helper.doWaitForEnd(process);
    }

    @Test
    void endUntrackedProcess() {
        Process process = mock(Process.class);
        helper.doWaitForEnd(process);
    }

    @Test
    void properlyTerminatedShouldNotLeak() {
        Process process = mock(Process.class);

        helper.doStartProcess(transaction, process, "hello");
        assertThat(storageMap).isNotEmpty();

        helper.doWaitForEnd(process);
        assertThat(storageMap)
            .describedAs("should remove process in map at end")
            .isEmpty();
    }

    @Test
    void waitForWithTimeoutDoesNotEndProcessSpan() {
        Process process = mock(Process.class);
        when(process.exitValue()).thenThrow(IllegalThreadStateException.class);

        helper.doStartProcess(transaction, process, "hello");

        helper.doWaitForEnd(process);
        assertThat(storageMap)
            .describedAs("waitFor exit without exit status should not terminate span")
            .isNotEmpty();
    }

}
