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
package co.elastic.apm.agent.context;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Tracer;

/**
 * A {@link LifecycleListener} notifies about the start and stop event of the {@link ElasticApmTracer}.
 * <p>
 * Implement this interface and register it as a {@linkplain java.util.ServiceLoader service} under
 * {@code src/main/resources/META-INF/services/co.elastic.apm.agent.context.LifecycleListener}.
 * </p>
 * <p>
 * Implementations may have a constructor with an {@link ElasticApmTracer} argument
 * </p>
 */
public interface LifecycleListener {

    /**
     * Callback for tracer initialization. As opposed to {@link LifecycleListener#start(ElasticApmTracer)}, which may
     * be called in a delay, this callback is called at the bootstrap of the JVM, before anything else start.
     * This may be useful for listeners that need to operate very early on, for example such that setup class loading
     * requirement to support OSGi systems.
     * @param tracer the tracer
     * @throws Exception
     */
    void init(ElasticApmTracer tracer) throws Exception;

    /**
     * Callback for when the {@link ElasticApmTracer} starts.
     *
     * @param tracer The tracer.
     */
    void start(ElasticApmTracer tracer) throws Exception;

    /**
     * Callback for when {@link ElasticApmTracer#pause()} has been called.
     * <p>
     * Typically, this method is used to reduce overhead on the application to a minimum. This can be done by cleaning
     * up resources like object pools, as well as by avoiding tracing-related overhead.
     * </p>
     * <p>
     * Exceptions thrown from this method are caught and handled so that they don't prevent further cleanup actions.
     * </p>
     *
     * @throws Exception When something goes wrong performing the cleanup.
     */
    void pause() throws Exception;

    /**
     * Callback for when {@link ElasticApmTracer#resume()} has been called.
     * <p>
     * Typically, used in order to revert the actions taken by the {@link LifecycleListener#pause()} method, allowing
     * the agent to restore all tracing capabilities
     * </p>
     * <p>
     * Exceptions thrown from this method are caught and handled so that they don't prevent further resume actions.
     * </p>
     *
     * @throws Exception When something goes wrong while attempting to resume.
     */
    void resume() throws Exception;

    /**
     * Callback for when {@link ElasticApmTracer#stop()} has been called.
     * <p>
     * Typically, this method is used to clean up resources like thread pools
     * so that there are no class loader leaks when a webapp is redeployed in an application server.
     * </p>
     * <p>
     * Exceptions thrown from this method are caught and handled so that they don't prevent further cleanup actions.
     * </p>
     * <p>
     * The order of the shutdown process is as follows:
     * </p>
     * <ol>
     *     <li>
     *         The {@link LifecycleListener#stop()} method of all lifecycle listeners is called.
     *         The order in which lifecycle listeners are called is non-deterministic.
     *     </li>
     *     <li>
     *         The {@link ElasticApmTracer#getSharedSingleThreadedPool()} is shut down gracefully,
     *         waiting a moment for the already scheduled tasks to be completed.
     *         This means that implementations of this method can schedule a last command to this pool that is executed before shutdown.
     *         The {@link Tracer#getState()} will still be {@link Tracer.TracerState#RUNNING} in the tasks scheduled to
     *         {@link ElasticApmTracer#getSharedSingleThreadedPool()} within this method.
     *     </li>
     *     <li>
     *         The tracer state is set to {@link co.elastic.apm.agent.impl.Tracer.TracerState#STOPPED}.
     *     </li>
     *     <li>
     *         The {@link co.elastic.apm.agent.report.Reporter} is closed.
     *     </li>
     * </ol>
     *
     * @throws Exception When something goes wrong performing the cleanup.
     */
    void stop() throws Exception;

}
