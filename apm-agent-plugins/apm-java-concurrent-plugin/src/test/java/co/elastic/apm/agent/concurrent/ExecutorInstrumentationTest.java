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
package co.elastic.apm.agent.concurrent;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecutorInstrumentationTest extends AbstractInstrumentationTest {

    private Executor executor;
    private Transaction transaction;

    private void init(Supplier<ExecutorService> supplier) {
        executor = supplier.get();
    }

    public static Stream<Arguments> args() {
        List<Supplier<Executor>> params = Arrays.asList(SimpleAsyncTaskExecutor::new, SyncTaskExecutor::new);
        return params.stream().map(k -> Arguments.of(k)).collect(Collectors.toList()).stream();
    }

    @BeforeEach
    public void setUp() {
        transaction = tracer.startRootTransaction(null).withName("Transaction").activate();
    }

    @AfterEach
    public void tearDown() {
        assertThat(tracer.getActive()).isNull();
    }

    @ParameterizedTest
    @MethodSource("args")
    public void testExecutorExecute_Transaction(Supplier<ExecutorService> serviceSupplier) {
        init(serviceSupplier);

        executor.execute(this::createAsyncSpan);
        assertOnlySpanIsChildOfOnlyTransaction();
    }

    @ParameterizedTest
    @MethodSource("args")
    public void testExecutorExecute_Span(Supplier<ExecutorService> serviceSupplier) {
        init(serviceSupplier);

        Span nonAsyncSpan = transaction.createSpan().withName("NonAsync").activate();
        executor.execute(this::createAsyncSpan);
        try {
            // wait for the async operation to end
            assertThat(reporter.getFirstSpan(1000)).isNotNull();
        } finally {
            nonAsyncSpan.deactivate().end();
            transaction.deactivate().end();
        }
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(2);
        assertThat(nonAsyncSpan.isChildOf(transaction)).isTrue();
        assertThat(reporter.getFirstSpan().isChildOf(nonAsyncSpan)).isTrue();
    }

    private void assertOnlySpanIsChildOfOnlyTransaction() {
        try {
            // wait for the async operation to end
            assertThat(reporter.getFirstSpan(1000)).isNotNull();
        } finally {
            transaction.deactivate().end();
        }
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getFirstSpan().isChildOf(reporter.getFirstTransaction())).isTrue();
    }

    private void createAsyncSpan() {
        assertThat(tracer.currentTransaction()).isEqualTo(transaction);
        tracer.getActive().createSpan().withName("Async").end();
    }
}
