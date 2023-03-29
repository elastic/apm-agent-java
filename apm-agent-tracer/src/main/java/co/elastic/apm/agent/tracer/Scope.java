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
package co.elastic.apm.agent.tracer;

/**
 * Within a scope, a {@link AbstractSpan} is active on the current thread.
 * Calling {@link #close()} detaches them from the active thread.
 * In a scope, you can get the currently active {@link AbstractSpan} via
 * {@link Tracer#getActive()}.
 * <p>
 * During the duration of a {@link AbstractSpan},
 * it can be active multiple times on multiple threads.
 * In applications with a single thread per request model,
 * there is typically one scope which lasts for the lifetime of the {@link AbstractSpan}.
 * In reactive applications, this model does not work, as a request is handled in multiple threads.
 * These types of application still might find it useful to scope a {@link AbstractSpan} on the currently processing thread.
 * For example, an instrumentation for {@link java.util.concurrent.ExecutorService} might want to propagate the currently
 * active {@link AbstractSpan} to thread which runs {@link java.util.concurrent.ExecutorService#execute(Runnable)},
 * so that {@link Tracer#getActive()} returns the expected {@link AbstractSpan}.
 * </p>
 * <p>
 * Note: {@link #close() closing} a scope does not {@link AbstractSpan#end() end} it's active
 * {@link AbstractSpan}.
 * </p>
 */
public interface Scope extends AutoCloseable {

    @Override
    void close();
}
