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
 * Annotated with {@link GlobalState} because it holds global mappings from original appender to corresponding
 * ECS-appender and from ECS-formatter to original formatter. Otherwise, it would be loaded by the plugin class loader
 * due to it's package.
 * <br><br>
 * <p>
 * Terminology:
 * <ul>
 *     <li>
 *         ECS-appender - a file appender that is configured to write original log events to a different file in ECS format
 *     </li>
 *     <li>
 *         ECS-formatter - the actual entity that is used by the logging framework to do the ECS-formatting. In log4j
 *         this would be a type of {@code Layout} and in Logback it would be a type of {@code Encoder}.
 *     </li>
 * </ul>
 *
 * <p>Following is the general algorithm that:</p>
 * <ul>
 *     <li>enables dynamic configuration of {@link LogEcsReformatting}</li>
 *     <li>
 *         (almost) guarantees that each log event is logged exactly once (more details {@link #configForCurrentLogEvent below})
 *     </li>
 *     <li>
 *         ensures lazy creation of ECS log files only if and when {@link LogEcsReformatting#SHADE SHADE} or
 *         {@link LogEcsReformatting#REPLACE REPLACE} are set
 *     </li>
 * </ul>
 * <pre>
 *      {@link #onAppendEnter(Object) on append() enter}
 *          get log_ecs_reformatting config and set as thread local
 *          if OVERRIDE
 *              if the current appender already uses an ECS-formatter - nothing to do
 *              else
 *                  look for ECS-appender mapped to current appender
 *                      if there is no such - create an EcsFormatterHolder and map it to original appender
 *                  map original formatter to ECS-formatter
 *                  replace original formatter with ECS-formatter in current appender
 *          else
 *              if the current appender already uses an ECS-formatter - restore original appender
 *              if SHADE or REPLACE
 *                  look for ECS-appender mapped to current appender
 *                      if there is no such - create one and add to map
 *                      else if it is an EcsFormatterHolder
 *                          create a real ECS-appender and replace mapping
 *                  if REPLACE and there is a valid ECS-appender - skip append() execution on original appender
 *      on getLayout() exit (only relevant to log4j2 where the appender is not exposing setLayout())
 *          if OVERRIDE - return the ECS-formatter instead of the original
 *      {@link #onAppendExit() on append() exit}
 *          if SHADE or REPLACE - invoke append() on ECS-appender
 *          clear log_ecs_reformatting config from thread local
 * </pre>
 * <br>
 *
 * @param <A> logging-framework-specific Appender type
 * @param <F> logging-framework-specific formatter type ({@code Layout} in Log4j, {@code Encoder} in Logback)
 */
@GlobalState
public abstract class AbstractLogShadingHelper<A, F> {

    // Escape shading
    private static final String ECS_LOGGING_PACKAGE_NAME = "co!elastic!logging".replace('!', '.');

    private static final Logger logger = LoggerFactory.getLogger(AbstractLogShadingHelper.class);
    public static final String ECS_SHADE_APPENDER_NAME = "EcsShadeAppender";

    private static final Object NULL_APPENDER = new Object();

    private static final CallDepth callDepth = CallDepth.get(AbstractLogShadingHelper.class);

    /**
     * A mapping between original appender and the corresponding custom ECS-appender.
     * The custom appender could either be a real ECS file appender, or it could be an {@code EcsFormatterHolder}.
     */
    private static final WeakConcurrentMap<Object, Object> originalAppenderToEcsAppender = WeakMapSupplier.createMap();

    /**
     * A mapping between the ECS-formatter the corresponding original appender.
     */
    private static final WeakConcurrentMap<Object, Object> ecsFormatterToOriginalFormatter = WeakMapSupplier.createMap();

    /**
     * This state is set at the beginning of {@link #onAppendEnter(Object)} cleared at the end of {@link #onAppendExit()}.
     * This ensures consistency during the entire handling of each log events and (almost) guarantees that each log
     * event is being logged exactly once. In very rare case, where the config changes from REPLACE to other during
     * concurrent logging events, a logging event may be missed. No duplicated logging for single event is guaranteed.
     * No need to use {@link co.elastic.apm.agent.sdk.state.GlobalThreadLocal} because we already annotate the class
     * with {@link GlobalState}.
     */
    private static final ThreadLocal<LogEcsReformatting> configForCurrentLogEvent = new ThreadLocal<>();

    private final LoggingConfiguration loggingConfiguration;
    @Nullable

    private final String configuredServiceName;

    public AbstractLogShadingHelper() {
        ElasticApmTracer tracer = GlobalTracer.requireTracerImpl();
        loggingConfiguration = tracer.getConfig(LoggingConfiguration.class);
        configuredServiceName = new ServiceFactory().createService(tracer.getConfig(CoreConfiguration.class), "").getName();
    }

    /**
     * Must be called exactly once at the enter to each {@code append()} method (or equivalent) invocation in order to properly
     * detect nested invocations.
     * <p>Algorithm:</p>
     * <pre>
     *  if this is a nested append() call - do nothing
     *  get log_ecs_reformatting config and set as thread local
     *      if OVERRIDE
     *          if the current appender already uses an ECS-formatter - nothing to do
     *          else
     *              look for ECS-appender mapped to current appender
     *                  if there is no such - create an EcsFormatterHolder and map it to original appender
     *              map original formatter to ECS-formatter
     *              replace original formatter with ECS-formatter in current appender
     *      else
     *          if the current appender already uses an ECS-formatter - restore original appender
     *          if SHADE or REPLACE
     *              look for ECS-appender mapped to current appender
     *                  if there is no such - create one and add to map
     *                  else if it is an EcsFormatterHolder
     *                      create a real ECS-appender and replace mapping
     *              if REPLACE and there is a valid ECS-appender - skip append() execution on original appender
     * </pre>
     * @param originalAppender the original appender
     * @return true if log events should be ignored for the given appender; false otherwise
     */
    public boolean onAppendEnter(final A originalAppender) {
        if (callDepth.isNestedCallAndIncrement()) {
            // If this is a nested call, never skip, as it means that the decision not to skip was already made in the
            // outermost invocation
            return false;
        }

        LogEcsReformatting reformattingConfig = loggingConfiguration.getLogEcsReformatting();
        configForCurrentLogEvent.set(reformattingConfig);

        F originalFormatter = getFormatterFrom(originalAppender);
        boolean isUsingEcsFormatter = isEcsFormatter(originalFormatter);
        if (reformattingConfig == LogEcsReformatting.OVERRIDE) {
            if (!isUsingEcsFormatter) {
                startOverriding(originalAppender, originalFormatter);
            }
        } else {
            if (isUsingEcsFormatter) {
                stopOverriding(originalAppender);
            }
            if (reformattingConfig == LogEcsReformatting.SHADE || reformattingConfig == LogEcsReformatting.REPLACE) {
                Object ecsAppender = originalAppenderToEcsAppender.get(originalAppender);
                if (ecsAppender == null || isMockAppender(ecsAppender)) {
                    createAndMapShadeAppenderFor(originalAppender);
                    ecsAppender = originalAppenderToEcsAppender.get(originalAppender);
                }
                // if ECS-reformatting is configured to REPLACE the original file, and there is a valid shade appender, then
                // it is safe enough to skip execution. And since we skip, no need to worry about nested calls.
                return reformattingConfig == LogEcsReformatting.REPLACE && ecsAppender != NULL_APPENDER;
            }
        }
        return false;
    }

    /**
     * Starts overriding the given appender - replaces formatter in original appender and handles mapping
     * @param originalAppender the framework appender for the original log
     * @param originalFormatter the original formatter
     */
    private void startOverriding(A originalAppender, F originalFormatter) {
        F ecsFormatter;
        synchronized (originalAppenderToEcsAppender) {
            Object ecsAppender = originalAppenderToEcsAppender.get(originalAppender);
            if (ecsAppender == null || ecsAppender == NULL_APPENDER) {
                String serviceName = getServiceName();
                ecsFormatter = createEcsFormatter(getEventDataset(originalAppender, serviceName), serviceName);
                originalAppenderToEcsAppender.put(originalAppender, new EcsFormatterHolder(ecsFormatter));
            } else if (ecsAppender.getClass().getName().equals(EcsFormatterHolder.class.getName())) {
                // race condition - another thread already mapped EcsFormatterHolder for this appender
                //noinspection unchecked
                ecsFormatter = ((EcsFormatterHolder) ecsAppender).getEcsFormatter();
            } else {
                // already mapped to an ECS appender
                ecsFormatter = getFormatterFrom((A) ecsAppender);
            }
        }
        synchronized (ecsFormatterToOriginalFormatter) {
            if (!ecsFormatterToOriginalFormatter.containsKey(ecsFormatter)) {
                setFormatter(originalAppender, ecsFormatter);
                ecsFormatterToOriginalFormatter.put(ecsFormatter, originalFormatter);
            }
        }
    }

    /**
     * Stops overriding log events in the given appender
     * @param appender appender to stop overriding
     */
    private void stopOverriding(A appender) {
        synchronized (ecsFormatterToOriginalFormatter) {
            F ecsFormatter = getFormatterFrom(appender);
            if (isEcsFormatter(ecsFormatter)) {
                F originalAppender = (F) ecsFormatterToOriginalFormatter.remove(ecsFormatter);
                if (originalAppender != null) {
                    setFormatter(appender, originalAppender);
                }
            }
        }
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
        try {
            LogEcsReformatting logEcsReformatting = configForCurrentLogEvent.get();
            return logEcsReformatting == LogEcsReformatting.SHADE || logEcsReformatting == LogEcsReformatting.REPLACE;
        } finally {
            configForCurrentLogEvent.remove();
        }
    }

    @Nullable
    protected String getConfiguredShadeDir() {
        return loggingConfiguration.getLogEcsFormattingDestinationDir();
    }

    private void createAndMapShadeAppenderFor(final A originalAppender) {
        synchronized (originalAppenderToEcsAppender) {
            Object ecsAppender = originalAppenderToEcsAppender.get(originalAppender);
            if (ecsAppender == NULL_APPENDER) {
                return;
            }

            if (isShadingAppender(originalAppender) || isEcsFormatter(getFormatterFrom(originalAppender))) {
                originalAppenderToEcsAppender.put(originalAppender, NULL_APPENDER);
                return;
            }

            if (ecsAppender == null || isMockAppender(ecsAppender)) {
                String serviceName = getServiceName();
                F ecsFormatter = createEcsFormatter(getEventDataset(originalAppender, serviceName), serviceName);
                A createdAppender = createAndStartEcsAppender(originalAppender, ECS_SHADE_APPENDER_NAME, ecsFormatter);
                originalAppenderToEcsAppender.put(originalAppender, createdAppender != null ? createdAppender : NULL_APPENDER);
            }
        }
    }

    private boolean isMockAppender(Object ecsAppender) {
        return ecsAppender.getClass().getName().startsWith("co.elastic.apm.agent");
    }

    /**
     * Looks up an ECS-formatter to override logging events in the given appender
     * @param originalAppender the original log appender
     * @return an ECS-formatter if such is mapped to the provide appender and the caller should override, or {@code null}
     */
    @Nullable
    public F getEcsOverridingFormatterFor(A originalAppender) {
        F ecsFormatter = null;
        if (configForCurrentLogEvent.get() == LogEcsReformatting.OVERRIDE) {
            Object ecsAppender = originalAppenderToEcsAppender.get(originalAppender);
            if (ecsAppender != null && ecsAppender.getClass().getName().equals(EcsFormatterHolder.class.getName())) {
                ecsFormatter = ((EcsFormatterHolder) ecsAppender).getEcsFormatter();
            }
        }
        return ecsFormatter;
    }

    /**
     * Returns either a valid ECS file appender that corresponds the given appender, or {@code null} if such does not exist
     * @param originalAppender the original log appender to base the lookup on
     * @return a valid ECS file appender or {@code null} if such does not available
     */
    @Nullable
    public A getShadeAppenderFor(A originalAppender) {
        Object ecsAppender = originalAppenderToEcsAppender.get(originalAppender);
        if (ecsAppender != null) {
            if (ecsAppender == NULL_APPENDER || isMockAppender(ecsAppender)) {
                ecsAppender = null;
            }
        }
        return (A) ecsAppender;
    }

    /**
     * Close the ECS shade appender that corresponds the given appender. Normally called when the original appender
     * gets closed.
     * @param originalAppender original appender for which shade appender should be closed
     */
    public void closeShadeAppenderFor(A originalAppender) {
        synchronized (originalAppenderToEcsAppender) {
            Object shadeAppender = originalAppenderToEcsAppender.remove(originalAppender);
            if (shadeAppender != null && shadeAppender != NULL_APPENDER && !isMockAppender(shadeAppender)) {
                closeShadeAppender((A) shadeAppender);
            }
        }
    }

    /**
     * Checks whether the given appender is a shading appender, so to avoid recursive shading
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
     * @param formatter formatter
     * @return true if the provided formatter an ECS-formatter; false otherwise
     */
    private boolean isEcsFormatter(F formatter) {
        return formatter.getClass().getName().startsWith(ECS_LOGGING_PACKAGE_NAME);
    }

    /**
     * Returns the underlying entity that is responsible for the actual ECS formatting, e.g. the encoder or layout.
     *
     * @param appender used appender
     * @return the given appender's formatting entity
     */
    protected abstract F getFormatterFrom(A appender);

    /**
     * Sets the given formatter to the given appender.
     *
     * @param appender  appender
     * @param formatter formatter
     */
    protected abstract void setFormatter(A appender, F formatter);

    @Nullable
    protected abstract String getAppenderName(A appender);

    @SuppressWarnings("SameParameterValue")
    @Nullable
    protected abstract A createAndStartEcsAppender(A originalAppender, String ecsAppenderName, F ecsFormatter);

    protected abstract F createEcsFormatter(String eventDataset, @Nullable String serviceName);

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
    private String getServiceName() {
        return configuredServiceName;
    }

    /**
     * Computes a proper value for the ECS {@code event.dataset} field based on the service name and the appender name
     *
     * @param appender    the appender for which event dataset is to be calculated
     * @param serviceName service name to prefix the {@code event.dataset}
     * @return event dataset in the form of {@code <service-name>.<appender-name>}, or {@code <service-name>.log}
     */
    private String getEventDataset(A appender, @Nullable String serviceName) {
        String appenderName = getAppenderName(appender);
        if (appenderName == null) {
            appenderName = "log";
        }
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

    /*********************************************************************************************************************

     /**
     * A mock {@code Appender} used for holding a reference to the original formatter.
     */
    private class EcsFormatterHolder {
        private final F ecsFormatter;

        EcsFormatterHolder(F ecsFormatter) {
            this.ecsFormatter = ecsFormatter;
        }

        public F getEcsFormatter() {
            return ecsFormatter;
        }
    }
}
