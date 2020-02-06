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
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

public class ExcludedExecutorClassTest extends AbstractInstrumentationTest {

    private ExecutorService executor;
    private Transaction transaction;

    @Before
    public void setUp() {
        executor = new ExecutorServiceWrapper(Executors.newFixedThreadPool(1));
        ExecutorInstrumentation.excludedClasses.add(ExecutorServiceWrapper.class.getName());
        transaction = tracer.startRootTransaction(null).withName("Transaction").activate();
    }

    @After
    public void tearDown() {
        transaction.deactivate().end();
        ExecutorInstrumentation.excludedClasses.remove(ExecutorServiceWrapper.class.getName());
    }

    @Test
    public void testExecutorExecute() throws Exception {
        assertThat(executor.submit(tracer::getActive).get()).isNull();
    }
}
