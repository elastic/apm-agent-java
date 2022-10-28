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
package co.elastic.apm.agent.util;

import co.elastic.apm.agent.common.ThreadUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class ExecutorUtilsTest {

    @AfterEach
    void cleanup() {
        ExecutorUtils.setThreadStartListener(null);
    }

    @Test
    void testSingleThreadSchedulingDaemonPool() throws ExecutionException, InterruptedException, TimeoutException {
        final String threadPurpose = "test-single-scheduling-pool";
        ThreadPoolExecutor singleThreadSchedulingDaemonPool = ExecutorUtils.createSingleThreadSchedulingDaemonPool(threadPurpose);
        executeTestOnThreadPool(singleThreadSchedulingDaemonPool, threadPurpose, 1);
        assertThat(ExecutorUtils.isAgentExecutor(singleThreadSchedulingDaemonPool)).isTrue();
    }

    @Test
    void testSingleThreadDaemonPool() throws ExecutionException, InterruptedException, TimeoutException {
        final String threadPurpose = "test-single-pool";
        ThreadPoolExecutor singleThreadDaemonPool = ExecutorUtils.createSingleThreadDaemonPool(threadPurpose, 5);
        executeTestOnThreadPool(singleThreadDaemonPool, threadPurpose, 1);
        assertThat(ExecutorUtils.isAgentExecutor(singleThreadDaemonPool)).isTrue();
    }

    @Test
    void testThreadDaemonPool() throws ExecutionException, InterruptedException, TimeoutException {
        final String threadPurpose = "test-pool";
        ThreadPoolExecutor threadDaemonPool = ExecutorUtils.createThreadDaemonPool(threadPurpose, 3, 5);
        executeTestOnThreadPool(threadDaemonPool, threadPurpose, 3);
        assertThat(ExecutorUtils.isAgentExecutor(threadDaemonPool)).isTrue();
    }

    private void executeTestOnThreadPool(ThreadPoolExecutor singleThreadDaemonPool, String threadPurpose, int maxPoolSize)
        throws InterruptedException, ExecutionException, TimeoutException {

        ElasticThreadStateListener listener = Mockito.mock(ElasticThreadStateListener.class);
        ExecutorUtils.setThreadStartListener(listener);

        assertThat(singleThreadDaemonPool.getPoolSize()).isEqualTo(0);
        assertThat(singleThreadDaemonPool.getMaximumPoolSize()).isEqualTo(maxPoolSize);
        final ClassLoader agentClassLoader = ExecutorUtils.class.getClassLoader();

        AtomicReference<Thread> startedThread = new AtomicReference<>();

        try {
            Future<Boolean> future = singleThreadDaemonPool.submit(() -> {
                Thread currentThread = Thread.currentThread();
                verify(listener).elasticThreadStarted(same(currentThread), eq(threadPurpose));
                verifyNoMoreInteractions(listener);
                startedThread.set(currentThread);
                assertThat(ExecutorUtils.getStartedThreads().containsKey(currentThread)).isTrue();
                assertThat(currentThread.getName()).startsWith(ThreadUtils.addElasticApmThreadPrefix(threadPurpose));
                assertThat(currentThread.isDaemon()).isTrue();
                assertThat(currentThread.getContextClassLoader()).isEqualTo(agentClassLoader);
                return true;
            });
            assertThat(singleThreadDaemonPool.getPoolSize()).isEqualTo(1);
            assertThat(future.get(100, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            singleThreadDaemonPool.shutdown();
            singleThreadDaemonPool.awaitTermination(10, TimeUnit.SECONDS);
        }
        verify(listener).elasticThreadFinished(same(startedThread.get()));
        verifyNoMoreInteractions(listener);
    }
}
