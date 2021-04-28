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
import co.elastic.apm.agent.impl.payload.ServiceFactory;
import co.elastic.apm.agent.logging.LogEcsReformatting;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import co.elastic.apm.agent.sdk.state.CallDepth;
import co.elastic.apm.agent.sdk.state.GlobalState;
import co.elastic.apm.agent.sdk.weakmap.WeakMapSupplier;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    // Escape shading
    private static final String ecsLoggingPackageName = "co!elastic!logging".replace('!', '.');

    private static final Logger logger = LoggerFactory.getLogger(AbstractLogShadingHelper.class);
    public static final String ECS_SHADE_APPENDER_NAME = "EcsShadeAppender";

    private static final Object NULL_APPENDER = new Object();

    private static final CallDepth callDepth = CallDepth.get(AbstractLogShadingHelper.class);

    private final LoggingConfiguration loggingConfiguration;
    @Nullable
    private final String configuredServiceName;

    public AbstractLogShadingHelper() {
        ElasticApmTracer tracer = GlobalTracer.requireTracerImpl();
        loggingConfiguration = tracer.getConfig(LoggingConfiguration.class);
        configuredServiceName = new ServiceFactory().createService(tracer.getConfig(CoreConfiguration.class), "").getName();
    }

    private static final WeakConcurrentMap<Object, Object> appenderToShadeAppender = WeakMapSupplier.createMap();

    @Nullable
    protected String getConfiguredShadeDir() {
        return loggingConfiguration.getLogEcsFormattingDestinationDir();
    }

    @Nullable
    private A getOrCreateShadeAppenderFor(A originalAppender) {
        if (isShadingAppender(originalAppender) || isUsingEcsLogging(originalAppender)) {
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

    @Nullable
    public A getShadeAppenderFor(A originalAppender) {
        Object shadeAppender = appenderToShadeAppender.get(originalAppender);
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

    /**
     * Must be called exactly once at the enter to each {@code append()} method (or equivalent) invocation in order to properly
     * detect nested invocations.
     * This method checks whether we should skip {@code append()} invocations for log events for the given appender.
     * Log event appends should be skipped if they are replaced by ECS-formatted events, meaning if:
     *  - shading is enabled by configuration AND
     *  - replace is enabled by configuration AND
     *  - there is a shade appender for this appender (there isn't when this appender IS A shade appender or it is not a file appender)
     * @param appender the appender
     * @return true if log events should be ignored for the given appender; false otherwise
     */
    public boolean onAppendEnter(A appender) {
        if (callDepth.isNestedCallAndIncrement()) {
            // If this is a nested call, never skip, as it means that the skipping decision was already made in the
            // outermost invocation
            return false;
        }

        A shadeAppender = getOrCreateShadeAppenderFor(appender);
        // if ECS-reformatting is configured to REPLACE the original file, and there is a valid shade appender, then
        // it is safe enough to skip execution. And since we skip, no need to worry about nested calls.
        return loggingConfiguration.getLogEcsReformatting() == LogEcsReformatting.REPLACE && shadeAppender != null;
    }

    /**
     * Must be called exactly once at the exit from each {@code append()} method (or equivalent) invocation. This method checks
     * whether the current {@code append()} execution should result with an appended shaded event based on the configuration
     * AND whether this is the outermost execution in nested {@code append()} calls.
     * @return whether the current execution of {@code append()} should result with an ECS shaded append
     */
    public boolean onAppendExit() {
        // If this is a nested append() invocation, do not shade now, only at the outermost invocation
        if (callDepth.isNestedCallAndDecrement()) {
            return false;
        }
        LogEcsReformatting logEcsReformatting = loggingConfiguration.getLogEcsReformatting();
        return logEcsReformatting == LogEcsReformatting.SHADE || logEcsReformatting == LogEcsReformatting.REPLACE;
    }

    public boolean isOverrideConfigured() {
        return loggingConfiguration.getLogEcsReformatting() == LogEcsReformatting.OVERRIDE;
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

    /**
     * Checks if the user has set up ECS-logging separately as well. We cannot use rely on the actual class (e.g.
     * through {@code instanceof}) because the ECS-logging dependency used by this plugin is shaded and because we
     * are looking for ECS encoder/layout from an arbitrary version that could be loaded by any class loader.
     *
     * @param appender used appender
     * @return true if the provided appender already configured to use ECS formatting
     */
    private boolean isUsingEcsLogging(A appender) {
        return getFormatterClassName(appender).startsWith(ecsLoggingPackageName);
    }

    /**
     * Returns the name of the class name of the underlying that is responsible for the actual ECS formatting, e.g.
     * the encoder or layout. The purpose of this method is to provide the ability to check whether the appender is
     * already configured to use ECS-formatting (independently or through the Java agent)
     *
     * @param appender used appender
     * @return class name of the underlying formatting entity
     */
    protected abstract String getFormatterClassName(A appender);

    @Nullable
    protected abstract String getAppenderName(A appender);

    @Nullable
    protected abstract A createAndConfigureAppender(A originalAppender, String appenderName);

    /**
     * We currently get the same service name that is reported in the metadata document.
     * This may mismatch automatically-discovered service names (if not configured). However, we only set it
     * once when configuring our appender, so we can have only one service name. In addition, if we use the
     * in-context service name (eg through MDC), all log events that will not occur within a traced transaction
     * will get a the global service name.
     *
     * @return the configured service name or the globally-automatically-discovered one (not one that is context-dependent)
     */
    @Nullable
    protected String getServiceName() {
        return configuredServiceName;
    }

    /**
     * Computes a proper value for the ECS {@code event.dataset} field based on the service name and the appender name
     *
     * @param appender the appender for which event dataset is to be calculated
     * @return event dataset in the form of {@code <service-name>.<appender-name>}, or {@code <service-name>.log}
     */
    protected String getEventDataset(A appender) {
        String appenderName = getAppenderName(appender);
        if (appenderName == null) {
            appenderName = "log";
        }
        String serviceName = getServiceName();
        if (serviceName == null) {
            return appenderName;
        } else {
            return serviceName + "." + appenderName;
        }
    }

    protected long getMaxLogFileSize() {
        return loggingConfiguration.getLogFileSize();
    }

    protected abstract void closeShadeAppender(A shadeAppender);

    /*********************************************************************************************************************
     * Since we shade slf4j in submodules, the following methods provide a way to properly log a message to the agent log
     ********************************************************************************************************************/

    protected void logInfo(String message) {
        logger.info(message);
    }

    protected void logError(String message, Throwable throwable) {
        logger.error(message, throwable);
    }
}
