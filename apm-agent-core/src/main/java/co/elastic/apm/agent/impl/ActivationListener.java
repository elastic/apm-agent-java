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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;

/**
 * A callback for {@link TraceContextHolder} activation and deactivaiton events
 * <p>
 * The constructor can optionally have a {@link ElasticApmTracer} parameter.
 * </p>
 */
public interface ActivationListener {

    /**
     * A callback for {@link TraceContextHolder#activate()}
     *
     * @param context the {@link TraceContextHolder} which is being activated
     * @throws Throwable if there was an error while calling this method
     */
    void beforeActivate(TraceContextHolder<?> context) throws Throwable;

    /**
     * A callback for {@link TraceContextHolder#deactivate()}
     * <p>
     * Note: the corresponding span may already be {@link AbstractSpan#end() ended} and {@link AbstractSpan#resetState() recycled}.
     * That's why there is no {@link TraceContextHolder} parameter.
     * </p>
     *
     * @param deactivatedContext the context which has just been deactivated
     * @throws Throwable if there was an error while calling this method
     */
    void afterDeactivate(TraceContextHolder<?> deactivatedContext) throws Throwable;
}
