/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

public class CommonsExecAsyncInstrumentationTest extends AbstractInstrumentationTest {

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

        new DefaultExecutor().execute(new CommandLine(getJavaBinaryPath()).addArgument("-version"), handler);
        handler.waitFor();

        assertThat(future.isCompletedExceptionally())
            .describedAs("async process should have properly executed")
            .isFalse();

        return future;
    }

    private static String getJavaBinaryPath() {
        boolean isWindows = System.getProperty("os.name").startsWith("Windows");
        String executable = isWindows ? "java.exe" : "java";
        Path path = Paths.get(System.getProperty("java.home"), "bin", executable);
        if (!Files.isExecutable(path)) {
            throw new IllegalStateException("unable to find java path");
        }
        return path.toAbsolutePath().toString();
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
