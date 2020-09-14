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
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import co.elastic.apm.agent.sdk.state.GlobalState;
import co.elastic.apm.agent.sdk.weakmap.WeakMapSupplier;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;

import javax.annotation.Nullable;

/**
 * The abstract Log shading helper- loaded as part of the agent core (agent CL / bootstrap CL / System CL).
 * Annotated with {@link GlobalState} because it holds the global mapping from original appender to corresponding
 * shade-appender.
 *
 * @param <A> logging-framework-specific Appender type
 */
@GlobalState
public abstract class AbstractLogShadingHelper<A> {

    public static final String ECS_SHADE_APPENDER_NAME = "EcsShadeAppender";

    private static final Object NULL_APPENDER = new Object();

    private final ElasticApmTracer tracer;
    private final LoggingConfiguration loggingConfiguration;

    public AbstractLogShadingHelper() {
        this.tracer = GlobalTracer.requireTracerImpl();
        loggingConfiguration = tracer.getConfig(LoggingConfiguration.class);
    }

    private static final WeakConcurrentMap<Object, Object> appenderToShadeAppender = WeakMapSupplier.createMap();

    @Nullable
    public A getOrCreateShadeAppenderFor(A originalAppender) {
        if (isShadingAppender(originalAppender)) {
            return null;
        }

        Object shadeAppender = appenderToShadeAppender.get(originalAppender);
        if (shadeAppender == null) {
            synchronized (appenderToShadeAppender) {
                if (!appenderToShadeAppender.containsKey(originalAppender)) {
                    A createdAppender = createAndConfigureAppender(originalAppender, ECS_SHADE_APPENDER_NAME);
                    appenderToShadeAppender.put(originalAppender, createdAppender != null ? createdAppender : NULL_APPENDER);
                }
            }
            shadeAppender = appenderToShadeAppender.get(originalAppender);
        }
        return shadeAppender != NULL_APPENDER ? (A) shadeAppender : null;
    }

    public void stopShading(A originalAppender) {
        synchronized (appenderToShadeAppender) {
            Object shadeAppender = appenderToShadeAppender.remove(originalAppender);
            if (shadeAppender != null) {
                closeShadeAppender((A) shadeAppender);
            }
        }
    }

    public boolean isShadingEnabled() {
        return loggingConfiguration.isLogShadingEnabled();
    }

    /**
     * Checks whether the given appender is a shading appender, so to avoid recursive shading
     *
     * @return true if the provide appender is a shading appender; false otherwise
     */
    private boolean isShadingAppender(A appender) {
        //noinspection StringEquality
        return getAppenderName(appender) == ECS_SHADE_APPENDER_NAME;
    }

    protected abstract String getAppenderName(A appender);

    @Nullable
    protected abstract A createAndConfigureAppender(A originalAppender, String appenderName);

    protected String getServiceName() {
        String serviceName = tracer.getMetaData().getService().getName();
        if (serviceName == null) {
            serviceName = tracer.getConfig(CoreConfiguration.class).getServiceName();
        }
        return serviceName;
    }

    protected long getMaxLogFileSize() {
        return loggingConfiguration.getLogFileSize();
    }

    protected abstract void closeShadeAppender(A shadeAppender);
}
