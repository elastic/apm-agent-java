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
package co.elastic.apm.agent.log.shader;

import co.elastic.apm.agent.collections.DetachedThreadLocalImpl;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.ServerlessConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.metadata.Service;
import co.elastic.apm.agent.impl.metadata.ServiceFactory;
import co.elastic.apm.agent.logging.LogEcsReformatting;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.sdk.state.CallDepth;
import co.elastic.apm.agent.sdk.state.GlobalState;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

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
 *     <li>guarantees that each log event is logged exactly once</li>
 *     <li>
 *         ensures lazy creation of ECS log files only if and when {@link LogEcsReformatting#SHADE SHADE} or
 *         {@link LogEcsReformatting#REPLACE REPLACE} are set
 *     </li>
 * </ul>
 * <pre>
 *      {@link #onAppendEnter(Object) on append() enter}:
 *          get log_ecs_reformatting config and set as thread local
 *          if OVERRIDE:
 *              if there is no entry in the originalAppender2originalFormatter map, then (thread safely):
 *                  create an ECS-formatter
 *                  add an entry to originalAppender2originalFormatter map
 *                  add an entry to originalAppender2ecsFormatter map
 *                  replace original formatter with ECS-formatter in current appender
 *          else
 *              if the current appender uses an ECS-formatter (thread safely):
 *                  restore original formatter in current appender
 *                  remove entry from originalAppender2originalFormatter map
 *                  remove entry from originalAppender2ecsFormatter map
 *              if SHADE or REPLACE:
 *                  if there is no entry in the originalAppender2ecsAppender mpa, then (thread safely):
 *                      create an ECS-appender
 *                      add entry to originalAppender2ecsAppender map
 *                  if REPLACE and there is a valid ECS-appender:
 *                      skip append() execution on original appender
 *      on getLayout() exit:
 *          * only relevant to log4j2, see {@code co.elastic.apm.agent.log4j2.Log4j2AppenderGetLayoutAdvice} for details
 *          if OVERRIDE:
 *              return the ECS-formatter instead of the original formatter
 *      {@link #onAppendExit(Object) on append() exit}:
 *          if SHADE or REPLACE:
 *              invoke append() on ECS-appender
 *          clear log_ecs_reformatting config from thread local
 * </pre>
 * <br>
 *
 * @param <A> logging-framework-specific Appender type
 * @param <F> logging-framework-specific formatter type ({@code Layout} in Log4j, {@code Encoder} in Logback)
 */
@GlobalState
public abstract class AbstractEcsReformattingHelper<A, F> {

    // Escape shading
    private static final String ECS_LOGGING_PACKAGE_NAME = "co.elastic.logging";

    // We can use regular agent logging here as this class is loaded from the agent CL
    private static final Logger logger = LoggerFactory.getLogger(AbstractEcsReformattingHelper.class);

    public static final String ECS_SHADE_APPENDER_NAME = "EcsShadeAppender";

    // Used to cache the fact that ECS-formatter or ECS-appender are not created for a given appender
    private static final Object NULL_APPENDER = new Object();
    private static final Object NULL_FORMATTER = new Object();

    private static final CallDepth callDepth = CallDepth.get(AbstractEcsReformattingHelper.class);

    /**
     * A mapping between original appender and the corresponding ECS-appender.
     * Used when {@link LoggingConfiguration#logEcsReformatting log_ecs_reformatting} is set to
     * {@link LogEcsReformatting#SHADE SHADE} or {@link LogEcsReformatting#REPLACE REPLACE}.
     */
    private static final WeakMap<Object, Object> originalAppender2ecsAppender = WeakConcurrent.buildMap();

    /**
     * A mapping between original appender and the formatter that it had originally.
     * Used when {@link LoggingConfiguration#logEcsReformatting log_ecs_reformatting} is set to
     * {@link LogEcsReformatting#OVERRIDE OVERRIDE}.
     */
    private static final WeakMap<Object, Object> originalAppender2originalFormatter = WeakConcurrent.buildMap();

    /**
     * A mapping between original appender and the corresponding ECS-formatter.
     * Used when {@link LoggingConfiguration#logEcsReformatting log_ecs_reformatting} is set to
     * {@link LogEcsReformatting#OVERRIDE OVERRIDE}, currently only for the log4j2 instrumentation.
     */
    private static final WeakMap<Object, Object> originalAppender2ecsFormatter = WeakConcurrent.buildMap();

    /**
     * This state is set at the beginning of {@link #onAppendEnter(Object)} and cleared at the end of {@link #onAppendExit(Object)}.
     * This ensures consistency during the entire handling of each log events and guarantees that each log event is being
     * logged exactly once.
     * No need to use {@link DetachedThreadLocalImpl} because we already annotate the class
     * with {@link GlobalState}.
     */
    private static final ThreadLocal<LogEcsReformatting> configForCurrentLogEvent = new ThreadLocal<>();

    private final LoggingConfiguration loggingConfiguration;

    @Nullable
    private final String configuredServiceName;

    @Nullable
    private final String configuredServiceNodeName;

    @Nullable
    private final Map<String, String> additionalFields;

    public AbstractEcsReformattingHelper() {
        ElasticApmTracer tracer = GlobalTracer.requireTracerImpl();
        loggingConfiguration = tracer.getConfig(LoggingConfiguration.class);
        additionalFields = loggingConfiguration.getLogEcsReformattingAdditionalFields();
        Service service = new ServiceFactory().createService(
            tracer.getConfig(CoreConfiguration.class),
            "",
            tracer.getConfig(ServerlessConfiguration.class)
        );
        configuredServiceName = service.getName();
        if (service.getNode() != null) {
            configuredServiceNodeName = service.getNode().getName();
        } else {
            configuredServiceNodeName = null;
        }
    }

    /**
     * Must be called exactly once at the enter to each {@code append()} method (or equivalent) invocation in order to
     * properly detect nested invocations.
     * @param appender the instrumented appender
     * @return true if log events should be ignored for the given appender; false otherwise
     */
    public boolean onAppendEnter(final A appender) {
        if (callDepth.isNestedCallAndIncrement()) {
            // If this is a nested call, never skip, as it means that the decision not to skip was already made in the
            // outermost invocation
            return false;
        }

        LogEcsReformatting reformattingConfig = loggingConfiguration.getLogEcsReformatting();
        configForCurrentLogEvent.set(reformattingConfig);

        boolean currentlyOverriding = originalAppender2originalFormatter.containsKey(appender);
        if (reformattingConfig == LogEcsReformatting.OVERRIDE) {
            if (!currentlyOverriding) {
                startOverriding(appender);
            }
        } else {
            if (currentlyOverriding) {
                stopOverriding(appender);
            }
            if (reformattingConfig == LogEcsReformatting.SHADE || reformattingConfig == LogEcsReformatting.REPLACE) {
                Object ecsAppender = originalAppender2ecsAppender.get(appender);
                if (ecsAppender == null) {
                    ecsAppender = createAndMapShadeAppenderFor(appender);
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
     */
    private void startOverriding(A originalAppender) {
        synchronized (originalAppender2originalFormatter) {
            if (originalAppender2originalFormatter.containsKey(originalAppender)) {
                return;
            }

            Object mappedFormatter = NULL_FORMATTER;
            Object ecsFormatter = NULL_FORMATTER;
            try {
                if (shouldApplyEcsReformatting(originalAppender)) {
                    F originalFormatter = getFormatterFrom(originalAppender);
                    mappedFormatter = originalFormatter;
                    String serviceName = getServiceName();
                    F createdEcsFormatter = createEcsFormatter(
                        getEventDataset(originalAppender, serviceName), serviceName, configuredServiceNodeName,
                        additionalFields, originalFormatter
                    );
                    setFormatter(originalAppender, createdEcsFormatter);
                    ecsFormatter = createdEcsFormatter;
                }
            } catch (Throwable throwable) {
                logger.warn(String.format("Failed to replace formatter for log appender %s.%s. " +
                        "Log events for this appender will not be overridden.",
                    originalAppender.getClass().getName(), getAppenderName(originalAppender)), throwable);
            } finally {
                originalAppender2ecsFormatter.put(originalAppender, ecsFormatter);
                originalAppender2originalFormatter.put(originalAppender, mappedFormatter);
            }
        }
    }

    /**
     * Stops overriding log events in the given appender
     * @param appender appender to stop overriding
     */
    private void stopOverriding(A appender) {
        synchronized (originalAppender2originalFormatter) {
            Object originalFormatter = originalAppender2originalFormatter.remove(appender);
            if (originalFormatter != null && originalFormatter != NULL_FORMATTER) {
                try {
                    setFormatter(appender, (F) originalFormatter);
                } catch (Throwable throwable) {
                    logger.warn(String.format("Failed to replace formatter for log appender %s.%s. " +
                            "Log events for this appender are overridden with ECS-formatted events.",
                        appender.getClass().getName(), getAppenderName(appender)), throwable);
                }
            }
            originalAppender2ecsFormatter.remove(appender);
        }
    }

    /**
     * Must be called exactly once at the exit from each {@code append()} method (or equivalent) invocation. This method checks
     * whether the current {@code append()} execution should result with an appended shaded event based on the configuration
     * AND whether this is the outermost execution in nested {@code append()} calls.
     * @param appender the instrumented appender
     * @return an ECS-appender to append the current event if required and such exists; {@code null} otherwise
     */
    @Nullable
    public A onAppendExit(A appender) {
        // If this is a nested append() invocation, do not shade now, only at the outermost invocation
        if (callDepth.isNestedCallAndDecrement()) {
            return null;
        }

        A ecsAppender = null;
        try {
            LogEcsReformatting logEcsReformatting = configForCurrentLogEvent.get();
            if (logEcsReformatting == LogEcsReformatting.SHADE || logEcsReformatting == LogEcsReformatting.REPLACE) {
                Object mappedAppender = originalAppender2ecsAppender.get(appender);
                if (mappedAppender != null && mappedAppender != NULL_APPENDER) {
                    ecsAppender = (A) mappedAppender;
                }
            }
        } finally {
            configForCurrentLogEvent.remove();
        }
        return ecsAppender;
    }

    @Nullable
    protected String getConfiguredShadeDir() {
        return loggingConfiguration.getLogEcsFormattingDestinationDir();
    }

    private Object createAndMapShadeAppenderFor(final A originalAppender) {
        synchronized (originalAppender2ecsAppender) {
            Object ecsAppender = originalAppender2ecsAppender.get(originalAppender);
            if (ecsAppender != null) {
                return ecsAppender;
            }

            ecsAppender = NULL_APPENDER;
            try {
                if (shouldApplyEcsReformatting(originalAppender)) {
                    String serviceName = getServiceName();
                    F ecsFormatter = createEcsFormatter(
                        getEventDataset(originalAppender, serviceName), serviceName, configuredServiceNodeName,
                        additionalFields, getFormatterFrom(originalAppender)
                    );
                    ecsAppender = createAndStartEcsAppender(originalAppender, ECS_SHADE_APPENDER_NAME, ecsFormatter);
                    if (ecsAppender == null) {
                        ecsAppender = NULL_APPENDER;
                    }
                }
            } catch (Throwable throwable) {
                logger.warn(String.format("Failed to create ECS shade appender for log appender %s.%s. " +
                        "Log events for this appender will not be shaded.",
                    originalAppender.getClass().getName(), getAppenderName(originalAppender)), throwable);
            } finally {
                originalAppender2ecsAppender.put(originalAppender, ecsAppender);
            }
            return ecsAppender;
        }
    }

    private boolean shouldApplyEcsReformatting(A originalAppender) {
        F formatter = getFormatterFrom(originalAppender);
        return formatter != null &&
                !isShadingAppender(originalAppender) &&
                !isEcsFormatter(formatter) &&
                isAllowedFormatter(formatter, loggingConfiguration.getLogEcsFormatterAllowList());
    }

    protected boolean isAllowedFormatter(F formatter, List<WildcardMatcher> allowList) {
        return WildcardMatcher.anyMatch(allowList, formatter.getClass().getName()) != null;
    }

    /**
     * Looks up an ECS-formatter to override logging events in the given appender
     *
     * @param originalAppender the original log appender
     * @return an ECS-formatter if such is mapped to the provide appender and the caller should override, or {@code null}
     */
    @Nullable
    public F getEcsOverridingFormatterFor(A originalAppender) {
        F ecsFormatter = null;
        if (configForCurrentLogEvent.get() == LogEcsReformatting.OVERRIDE) {
            Object mappedFormatter = originalAppender2ecsFormatter.get(originalAppender);
            if (mappedFormatter != null && mappedFormatter != NULL_FORMATTER) {
                ecsFormatter = (F) mappedFormatter;
            }
        }
        return ecsFormatter;
    }

    /**
     * Closes the ECS shade appender that corresponds the given appender. Normally called when the original appender
     * gets closed.
     * @param originalAppender original appender for which shade appender should be closed
     */
    public void closeShadeAppenderFor(A originalAppender) {
        synchronized (originalAppender2ecsAppender) {
            Object shadeAppender = originalAppender2ecsAppender.remove(originalAppender);
            if (shadeAppender != null && shadeAppender != NULL_APPENDER) {
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
     * Checks if the given formatter comes from the usage of ECS-logging that was configured independently of the Java agent.
     * We cannot rely on the actual class (e.g. through {@code instanceof}) because the ECS-logging dependency used by
     * this plugin is shaded and because we are looking for ECS encoder/layout from an arbitrary version that could be
     * loaded by any class loader.
     *
     * @param formatter formatter
     * @return true if the provided formatter is an ECS-formatter; false otherwise
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
    @Nullable
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

    protected abstract F createEcsFormatter(String eventDataset, @Nullable String serviceName, @Nullable String serviceNodeName,
                                            @Nullable Map<String, String> additionalFields, F originalFormatter);

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
}
