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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class JavaRuntimeApiInstrumentationTest extends AbstractInstrumentationTest {

    @BeforeEach
    void startTransaction() {
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
        transaction.withName("Java Old Api");
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
    void exec() throws IOException, InterruptedException {
        Process exec = Runtime.getRuntime().exec("java -version");
        int exitValue = exec.waitFor();

        assertThat(exitValue).isEqualTo(0);

        assertThat(reporter.getSpans().size()).isEqualTo(1);
        final Span span = reporter.getFirstSpan();
        assertThat(span.getSubtype()).isEqualTo("java runtime api");
        assertThat(span.getType()).isEqualTo("execute");
        assertThat(span.getAction()).isEqualTo("execute");
        assertThat(span.getNameAsString()).isEqualTo("Execute");
    }

}
