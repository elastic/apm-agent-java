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
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutorUtilsTest {

    @Test
    void testSingleThreadSchedulingDaemonPool() throws ExecutionException, InterruptedException, TimeoutException {
        final String threadPurpose = "test-single-scheduling-pool";
        ThreadPoolExecutor singleThreadDaemonPool = ExecutorUtils.createSingleThreadSchedulingDaemonPool(threadPurpose);
        executeTestOnThreadPool(singleThreadDaemonPool, threadPurpose, 1);
    }

    @Test
    void testSingleThreadDaemonPool() throws ExecutionException, InterruptedException, TimeoutException {
        final String threadPurpose = "test-single-pool";
        ThreadPoolExecutor singleThreadDaemonPool = ExecutorUtils.createSingleThreadDaemonPool(threadPurpose, 5);
        executeTestOnThreadPool(singleThreadDaemonPool, threadPurpose, 1);
    }

    @Test
    void testThreadDaemonPool() throws ExecutionException, InterruptedException, TimeoutException {
        final String threadPurpose = "test-single-pool";
        ThreadPoolExecutor singleThreadDaemonPool = ExecutorUtils.createThreadDaemonPool(threadPurpose, 3, 5);
        executeTestOnThreadPool(singleThreadDaemonPool, threadPurpose, 3);
    }

    private void executeTestOnThreadPool(ThreadPoolExecutor singleThreadDaemonPool, String threadPurpose, int maxPoolSize)
        throws InterruptedException, ExecutionException, TimeoutException {
        assertThat(singleThreadDaemonPool.getPoolSize()).isEqualTo(0);
        assertThat(singleThreadDaemonPool.getMaximumPoolSize()).isEqualTo(maxPoolSize);
        final ClassLoader agentClassLoader = ExecutorUtils.class.getClassLoader();
        try {
            Future<Boolean> future = singleThreadDaemonPool.submit(() -> {
                Thread currentThread = Thread.currentThread();
                assertThat(currentThread.getName()).startsWith(ThreadUtils.addElasticApmThreadPrefix(threadPurpose));
                assertThat(currentThread.isDaemon()).isTrue();
                assertThat(currentThread.getContextClassLoader()).isEqualTo(agentClassLoader);
                return true;
            });
            assertThat(singleThreadDaemonPool.getPoolSize()).isEqualTo(1);
            assertThat(future.get(100, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            singleThreadDaemonPool.shutdown();
        }
    }
}
