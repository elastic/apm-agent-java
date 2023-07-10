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
package co.elastic.apm.agent.sdk.internal.util;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static co.elastic.apm.agent.sdk.internal.util.ExecutorUtils.ExecutorUtilsProvider;

public class ExecutorUtilsProviderTestImpl implements ExecutorUtilsProvider {

    @Override
    public boolean isAgentExecutor(Executor executor) {
        return executor instanceof SimpleScheduledThreadPoolExecutor;
    }

    @Override
    public ScheduledExecutorService createSingleThreadSchedulingDaemonPool(String threadPurpose) {
        return new SimpleScheduledThreadPoolExecutor();
    }

    @Override
    public void shutdownAndWaitTermination(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException();
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static class SimpleScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {

        private SimpleScheduledThreadPoolExecutor() {
            super(1);
        }
    }
}
