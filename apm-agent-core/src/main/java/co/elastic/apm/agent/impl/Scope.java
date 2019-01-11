/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.impl.transaction.TraceContextHolder;

/**
 * Within a scope, a {@link TraceContextHolder} is active on the current thread.
 * Calling {@link #close()} detaches them from the active thread.
 * In a scope, you can get the currently active {@link TraceContextHolder} via
 * {@link ElasticApmTracer#getActive()}.
 * <p>
 * During the duration of a {@link TraceContextHolder},
 * it can be active multiple times on multiple threads.
 * In applications with a single thread per request model,
 * there is typically one scope which lasts for the lifetime of the {@link TraceContextHolder}.
 * In reactive applications, this model does not work, as a request is handled in multiple threads.
 * These types of application still might find it useful to scope a {@link TraceContextHolder} on the currently processing thread.
 * For example, an instrumentation for {@link java.util.concurrent.ExecutorService} might want to propagate the currently
 * active {@link TraceContextHolder} to thread which runs {@link java.util.concurrent.ExecutorService#execute(Runnable)},
 * so that {@link ElasticApmTracer#getActive()} returns the expected {@link TraceContextHolder}.
 * </p>
 * <p>
 * Note: {@link #close() closing} a scope does not {@link TraceContextHolder#end() end} it's active {@link TraceContextHolder}.
 * </p>
 */
public interface Scope extends AutoCloseable {

    @Override
    void close();

    enum NoopScope implements Scope {
        INSTANCE;

        @Override
        public void close() {
            // noop
        }
    }
}
