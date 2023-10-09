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
import co.elastic.apm.agent.tracer.LifecycleListener;

public interface InitializableLifecycleListener extends LifecycleListener {

    /**
     * Callback for tracer initialization. As opposed to {@link LifecycleListener#start()}, which may
     * be called in a delay, this callback is called at the bootstrap of the JVM, before anything else starts.
     * This may be useful for listeners that need to operate very early on, for example such that setup class loading
     * requirement to support OSGi systems.
     */
    void init(ElasticApmTracer tracer) throws Exception;
}
