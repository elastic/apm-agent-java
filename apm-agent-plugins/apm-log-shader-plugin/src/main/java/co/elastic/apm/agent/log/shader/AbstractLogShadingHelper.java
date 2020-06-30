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
package co.elastic.apm.agent.log.shader;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;

import javax.annotation.Nullable;

/**
 * The abstract Log shading helper- loaded as part of the agent core (agent CL / bootstrap CL / System CL)
 * @param <A> logging-framework-specific Appender type
 */
public abstract class AbstractLogShadingHelper<A> {

    private final ElasticApmTracer tracer;
    private final LoggingConfiguration loggingConfiguration;
    private final String serviceName;

    public AbstractLogShadingHelper(ElasticApmTracer tracer) {
        this.tracer = tracer;
        loggingConfiguration = tracer.getConfig(LoggingConfiguration.class);
        serviceName = tracer.getConfig(CoreConfiguration.class).getServiceName();
    }

    private static final WeakConcurrentMap<Object, Object> appenderToShadeAppender = new WeakConcurrentMap.WithInlinedExpunction<Object, Object>();

    @Nullable
    public A getOrCreateShadeAppenderFor(A originalAppender) {
        if (isShadingAppender(originalAppender)) {
            return null;
        }

        Object shadeAppender = appenderToShadeAppender.get(originalAppender);
        if (shadeAppender == null) {
            synchronized (appenderToShadeAppender) {
                if (!appenderToShadeAppender.containsKey(originalAppender)) {
                    appenderToShadeAppender.put(originalAppender, createAndConfigureAppender(originalAppender));
                }
            }
            shadeAppender = appenderToShadeAppender.get(originalAppender);
        }
        return (A) shadeAppender;
    }

    public void stopShading(A originalAppender) {
        synchronized (appenderToShadeAppender) {
            Object shadeAppender = appenderToShadeAppender.remove(originalAppender);
            if (shadeAppender != null) {
                closeShadeAppender((A) shadeAppender);
            }
        }
    }

    /**
     * Checks whether the given appender is a shading appender, so to avoid recursive shading
     * @return true if the provide appender is a shading appender; false otherwise
     */
    protected abstract boolean isShadingAppender(A appender);

    protected abstract A createAndConfigureAppender(A originalAppender);

    protected String getServiceName() {
        return serviceName;
    }

    protected long getMaxLogFileSize() {
        return loggingConfiguration.getLogFileSize();
    }

    protected abstract void closeShadeAppender(A shadeAppender);
}
