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
package co.elastic.apm.agent.cmd;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommonsExecInstrumentationTest extends AbstractInstrumentationTest {

    private Executor executor = new DefaultExecutor();

    private CommandLine commandLine = new CommandLine("java").addArgument("-version");

    @BeforeEach
    void startTransaction() {
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
        transaction.withName("Commons Exec");
        transaction.withType("execute");
    }

    @AfterEach
    void endTransaction() {
        try {
            Transaction currentTransaction = tracer.currentTransaction();
            if (currentTransaction != null) {
                currentTransaction.deactivate().end();
            }
        } finally {
            reporter.reset();
        }
    }

    @Test
    void synchronous() throws IOException {
        int exitValue = executor.execute(commandLine);

        verifyProcessExecution(exitValue);
    }

    @Test
    void synchronousWithMap() throws IOException {
        Map<String, String> environment = new HashMap<>();
        environment.put("testKey", "testValue");

        int exitValue = executor.execute(commandLine, environment);

        verifyProcessExecution(exitValue);
    }

    @Test
    void asynchronous() throws IOException, InterruptedException {
        DefaultExecuteResultHandler handler = new DefaultExecuteResultHandler();

        executor.execute(commandLine, handler);

        // TODO: Should this be done in some other way?
        handler.waitFor();

        int exitValue = handler.getExitValue();

        verifyProcessExecution(exitValue);
    }

    @Test
    void asynchronousWithMap() throws IOException, InterruptedException {
        Map<String, String> environment = new HashMap<>();
        environment.put("testKey", "testValue");
        DefaultExecuteResultHandler handler = new DefaultExecuteResultHandler();

        executor.execute(commandLine, environment, handler);

        // TODO: Should this be done in some other way?
        handler.waitFor();

        int exitValue = handler.getExitValue();

        verifyProcessExecution(exitValue);
    }

    private void verifyProcessExecution(int exitValue) {
        assertThat(exitValue).isEqualTo(0);

        assertThat(reporter.getSpans().size()).isEqualTo(1);
        final Span span = reporter.getFirstSpan();
        assertThat(span.getSubtype()).isEqualTo("commons-exec");
        assertThat(span.getType()).isEqualTo("execute");
        assertThat(span.getAction()).isEqualTo("execute");
        assertThat(span.getNameAsString()).isEqualTo("Execute java");
    }

}
