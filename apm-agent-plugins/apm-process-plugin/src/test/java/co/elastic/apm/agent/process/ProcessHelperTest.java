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

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.TransactionUtils;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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

    private Transaction transaction;

    private WeakMap<Process, co.elastic.apm.agent.tracer.Span<?>> storageMap;
    private ProcessHelper helper;

    @BeforeEach
    void before() {
        transaction = new Transaction(tracer);
        TransactionUtils.fillTransaction(transaction);

        storageMap = WeakConcurrent.buildMap();
        helper = new ProcessHelper(storageMap);
    }

    @Test
    void checkSpanNaming() {
        Process process = mock(Process.class);

        String binaryName = "hello";
        String programName = Paths.get("bin", binaryName).toAbsolutePath().toString();

        helper.doStartProcess(transaction, process, programName);

        helper.doEndProcess(process, true);

        Span span = getFirstSpan();

        assertThat(span.getNameAsString()).isEqualTo(binaryName);
        assertThat(span.getType()).isEqualTo("process");
        assertThat(span.getSubtype()).isNull();
        assertThat(span.getAction()).isNull();
        assertThat(span.getOutcome()).isEqualTo(Outcome.SUCCESS);
    }

    @Test
    void startTwiceShouldIgnore() {
        Process process = mock(Process.class);

        helper.doStartProcess(transaction, process, "hello");
        Span span = (Span) storageMap.get(process);

        helper.doStartProcess(transaction, process, "hello");
        assertThat(storageMap.get(process))
            .describedAs("initial span should not be overwritten")
            .isSameAs(span);
        helper.doEndProcess(process, true);
    }

    @Test
    void endTwiceShouldIgnore() {
        Process process = mock(Process.class);

        helper.doStartProcess(transaction, process, "hello");
        assertThat(storageMap).isNotEmpty();

        helper.doEndProcess(process, true);

        // this second call should be ignored, thus exception not reported
        helper.doEndProcess(process, true);

        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getErrors())
            .describedAs("error should not be reported")
            .isEmpty();
    }

    @Test
    void endProcessAfterEndSpanShouldBeIgnored() {
        Process process = mock(Process.class);

        helper.doStartProcess(transaction, process, "hello");
        assertThat(storageMap).isNotEmpty();

        helper.doEndProcessSpan(process, 0);

        // this second call should be ignored, thus exception not reported
        helper.doEndProcess(process, true);

        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getErrors())
            .describedAs("error should not be reported")
            .isEmpty();
    }

    @Test
    void endSpanAfterEndProcessShouldBeIgnored() {
        Process process = mock(Process.class);

        helper.doStartProcess(transaction, process, "hello");
        assertThat(storageMap).isNotEmpty();

        helper.doEndProcess(process, true);

        // this second call should be ignored, thus exception not reported
        helper.doEndProcessSpan(process, 0);

        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getErrors())
            .describedAs("error should not be reported")
            .isEmpty();
    }

    @Test
    void executeMultipleProcessesInTransaction() {
        Process process = mock(Process.class);

        helper.doStartProcess(transaction, process, "hello");
        helper.doEndProcess(process, true);

        helper.doStartProcess(transaction, process, "hello");
        helper.doEndProcess(process, true);

        assertThat(reporter.getSpans()).hasSize(2);
    }

    @Test
    void endUntrackedProcess() {
        Process process = mock(Process.class);
        helper.doEndProcess(process, true);
    }

    @Test
    void properlyTerminatedShouldNotLeak() {
        Process process = mock(Process.class);

        helper.doStartProcess(transaction, process, "hello");
        assertThat(storageMap).isNotEmpty();

        helper.doEndProcess(process, true);
        assertThat(storageMap)
            .describedAs("should remove process in map at end")
            .isEmpty();
    }

    @Test
    void waitForWithTimeoutDoesNotEndProcessSpan() {
        Process process = mock(Process.class);
        when(process.exitValue())
            // 1st call process not finished
            .thenThrow(IllegalThreadStateException.class)
            // 2cnd call process finished successfully
            .thenReturn(0);

        helper.doStartProcess(transaction, process, "hello");

        helper.doEndProcess(process, true);
        assertThat(storageMap)
            .describedAs("waitFor exit without exit status should not terminate span")
            .isNotEmpty();

        helper.doEndProcess(process, true);
        assertThat(storageMap).isEmpty();

        Span span = getFirstSpan();
        assertThat(span.getOutcome()).isEqualTo(Outcome.SUCCESS);
    }

    @Test
    void destroyWithoutProcessTerminatedShouldEndSpan() {
        Process process = mock(Process.class);
        verifyNoMoreInteractions(process); // we should not even use any method of process

        // we have to opt-in to allow unknown outcome
        reporter.disableCheckUnknownOutcome();

        helper.doStartProcess(transaction, process, "hello");

        helper.doEndProcess(process, false);
        assertThat(storageMap)
            .describedAs("process span should be marked as terminated")
            .isEmpty();

        assertThat(getFirstSpan().getOutcome()).isEqualTo(Outcome.UNKNOWN);
    }

    private Span getFirstSpan() {
        assertThat(reporter.getSpans()).hasSize(1);
        return reporter.getSpans().get(0);
    }

}
