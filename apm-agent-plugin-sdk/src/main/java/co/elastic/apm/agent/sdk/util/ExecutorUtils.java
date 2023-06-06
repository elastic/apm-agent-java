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
package co.elastic.apm.agent.sdk.util;

import co.elastic.apm.agent.sdk.internal.InternalUtil;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public final class ExecutorUtils {

    private static final ExecutorUtilsProvider supplier;


    static {
        supplier = InternalUtil.getServiceProvider(ExecutorUtilsProvider.class);
    }

    public static ScheduledExecutorService createSingleThreadSchedulingDaemonPool(final String threadPurpose) {
        return supplier.createSingleThreadSchedulingDaemonPool(threadPurpose);
    }

    public static boolean isAgentExecutor(Executor executor) {
        return supplier.isAgentExecutor(executor);
    }

    public static void shutdownAndWaitTermination(ExecutorService executor) {
        supplier.shutdownAndWaitTermination(executor);
    }

    public interface ExecutorUtilsProvider {

        boolean isAgentExecutor(Executor executor);

        ScheduledExecutorService createSingleThreadSchedulingDaemonPool(String threadPurpose);

        void shutdownAndWaitTermination(ExecutorService executor);
    }
}
