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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;

/**
 * A callback for {@link AbstractSpan} and {@link ErrorCapture} activation and deactivation events
 * <p>
 * The constructor can optionally have a {@link ElasticApmTracer} parameter.
 * </p>
 */
public interface ActivationListener {

    /**
     * A callback for {@link AbstractSpan#activate()}
     *
     * @param span the {@link AbstractSpan} that is being activated
     * @throws Throwable if there was an error while calling this method
     */
    void beforeActivate(AbstractSpan<?> span) throws Throwable;

    /**
     * A callback for {@link ErrorCapture#activate()}
     *
     * @param error the {@link ErrorCapture} that is being activated
     * @throws Throwable if there was an error while calling this method
     */
    void beforeActivate(ErrorCapture error) throws Throwable;

    /**
     * A callback for {@link AbstractSpan#deactivate()}
     * <p>
     * Note: the corresponding span may already be {@link AbstractSpan#end() ended} and {@link AbstractSpan#resetState() recycled}.
     * That's why there is no {@link AbstractSpan} parameter.
     * </p>
     *
     * @param deactivatedSpan the context that has just been deactivated
     * @throws Throwable if there was an error while calling this method
     */
    void afterDeactivate(AbstractSpan<?> deactivatedSpan) throws Throwable;

    /**
     * A callback for {@link ErrorCapture#deactivate()}
     *
     * @param deactivatedError the error that has just been deactivated
     * @throws Throwable if there was an error while calling this method
     */
    void afterDeactivate(ErrorCapture deactivatedError) throws Throwable;
}
