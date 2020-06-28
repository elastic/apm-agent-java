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
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ForkJoinTask;

import static co.elastic.apm.agent.concurrent.InstrumentableForkJoinPool.newTask;
import static org.assertj.core.api.Assertions.assertThat;

public class ForkJoinPoolTest extends AbstractInstrumentationTest {

    private InstrumentableForkJoinPool pool;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        pool = new InstrumentableForkJoinPool();
        transaction = tracer.startRootTransaction(null).withName("transaction").activate();
    }

    @AfterEach
    void tearDown() {
        assertThat(tracer.getActive()).isEqualTo(transaction);
        transaction.deactivate().end();
    }

    @Test
    void testExecute() throws Exception {
        final ForkJoinTask<? extends AbstractSpan<?>> task = newTask(() -> tracer.getActive());
        pool.execute(task);
        assertThat(task.get()).isEqualTo(transaction);
    }

    @Test
    void testSubmit() throws Exception {
        assertThat(pool.submit(newTask(() -> tracer.getActive())).get()).isEqualTo(transaction);
    }

    @Test
    void testInvoke() throws Exception {
        assertThat(pool.invoke(newTask(() -> tracer.getActive()))).isEqualTo(transaction);
    }

}
