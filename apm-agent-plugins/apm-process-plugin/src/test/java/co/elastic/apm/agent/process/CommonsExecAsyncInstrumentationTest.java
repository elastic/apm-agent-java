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
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.test.JavaExecutable;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CommonsExecAsyncInstrumentationTest extends AbstractInstrumentationTest {

    private static final boolean isWindows = System.getProperty("os.name").startsWith("Windows");

    @Test
    void asyncProcessWithinTransaction() throws Exception {
        startTransaction();
        assertThat(asyncProcessHasTransactionContext().get())
            .describedAs("executor runnable not in the expected transaction context")
            .isNotNull();
        terminateTransaction();
    }

    @Test
    void asyncProcessOutsideTransaction() throws Exception {
        assertThat(asyncProcessHasTransactionContext().get())
            .describedAs("executor runnable should not be in transaction context")
            .isNull();
    }

    @Test
    void processWithExitValueCheck() throws Exception {
        startTransaction();
        List<String> cmd;
        if (isWindows) {
            // other options: bash -c "sleep 0.2" (bash not guaranteed)
            // powershell -nop -c "& {sleep -m 2}" (too long to start)
            // timeout /NOBREAK /T 1 (output redirection fails)
            cmd = List.of("ping", "192.0.2.1", "-n", "1", "-w", "200");
        } else {
            cmd = List.of("sleep", "0.5");
        }
        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        Process process = processBuilder.start();
        assertThatThrownBy(process::exitValue).isInstanceOf(IllegalThreadStateException.class);
        assertThat(reporter.getSpans().stream().filter((span) -> span.getNameAsString().startsWith(isWindows ? "ping" : "sleep"))).isEmpty();
        process.waitFor(1000, TimeUnit.MILLISECONDS);
        // Windows: either ping or the call times out, so it always returns exitcode 1
        assertThat(process.exitValue()).isEqualTo(isWindows ? 1 : 0);
        assertThat(reporter.getSpans().stream().filter((span) -> span.getNameAsString().startsWith(isWindows ? "ping" : "sleep"))).hasSize(1);
        terminateTransaction();
    }

    private static CompletableFuture<AbstractSpan<?>> asyncProcessHasTransactionContext() throws Exception {
        final CompletableFuture<AbstractSpan<?>> future = new CompletableFuture<>();
        DefaultExecuteResultHandler handler = new DefaultExecuteResultHandler() {

            // note: calling super is required otherwise process termination is not detected and waits forever

            @Override
            public void onProcessComplete(int exitValue) {
                super.onProcessComplete(exitValue);
                if (exitValue == 0) {
                    future.complete(tracer.getActive());
                } else {
                    future.completeExceptionally(new IllegalStateException("Exit value is not 0: " + exitValue));
                }
            }

            @Override
            public void onProcessFailed(ExecuteException e) {
                super.onProcessFailed(e);
                future.completeExceptionally(e);
            }
        };

        new DefaultExecutor().execute(new CommandLine(JavaExecutable.getBinaryPath()).addArgument("-version"), handler);
        handler.waitFor();

        assertThat(future.isCompletedExceptionally())
            .describedAs("async process should have properly executed")
            .isFalse();

        return future;
    }

    private static void startTransaction() {
        Transaction transaction = tracer.startRootTransaction(CommonsExecAsyncInstrumentationTest.class.getClassLoader());

        assertThat(transaction).isNotNull();
        transaction.withType("request")
            .withName("parent transaction")
            .activate();
    }

    private static void terminateTransaction() {
        Transaction transaction = tracer.currentTransaction();
        assertThat(transaction).isNotNull();
        transaction.deactivate().end();

        reporter.assertRecycledAfterDecrementingReferences();
    }

}
