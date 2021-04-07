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
package co.elastic.apm.agent.bci;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;

/**
 * The constructor can optionally have a {@link ElasticApmTracer} parameter.
 */
public abstract class TracerAwareInstrumentation extends ElasticApmInstrumentation {

    @VisibleForAdvice
    public static final Tracer tracer = GlobalTracer.get();

    /**
     * Allows to opt-out of indy plugins.
     * This is just to allow for a migration period where both indy and non-indy plugins are in use.
     * Once all non-indy plugins are migrated this method will be removed.
     *
     * @deprecated Overriding this method means not the instrumentation is not an indy plugin.
     * The usage of non-indy plugins is deprecated.
     * @return whether to load the classes of this plugin in dedicated plugin class loaders (one for each unique class loader)
     * and dispatch to the {@linkplain #getAdviceClassName() advice} via an {@code INVOKEDYNAMIC} instruction.
     */
    @Deprecated
    public boolean indyPlugin() {
        return true;
    }

}
