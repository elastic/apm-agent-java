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
package co.elastic.apm.agent.logging;

import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.ServiceNameUtil;
import co.elastic.logging.log4j2.EcsLayout;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.stagemonitor.configuration.ConfigurationOption;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;
import java.util.List;

import static co.elastic.apm.agent.logging.LoggingConfiguration.AGENT_HOME_PLACEHOLDER;
import static co.elastic.apm.agent.logging.LoggingConfiguration.DEFAULT_LOG_FILE;
import static co.elastic.apm.agent.logging.LoggingConfiguration.DEPRECATED_LOG_FILE_KEY;
import static co.elastic.apm.agent.logging.LoggingConfiguration.DEPRECATED_LOG_LEVEL_KEY;
import static co.elastic.apm.agent.logging.LoggingConfiguration.LOG_FILE_KEY;
import static co.elastic.apm.agent.logging.LoggingConfiguration.LOG_LEVEL_KEY;
import static co.elastic.apm.agent.logging.LoggingConfiguration.SYSTEM_OUT;

public class Log4j2ConfigurationFactory extends ConfigurationFactory {

    static {
        PluginManager.addPackage(EcsLayout.class.getPackage().getName());
        PluginManager.addPackage(LoggerContext.class.getPackage().getName());
    }

    private final List<org.stagemonitor.configuration.source.ConfigurationSource> sources;

    public Log4j2ConfigurationFactory(List<org.stagemonitor.configuration.source.ConfigurationSource> sources) {
        this.sources = sources;
    }

    /**
     * The ConfigurationRegistry uses and thereby initializes a logger,
     * so we can't use it here initialize the {@link ConfigurationOption}s in this class.
     */
    private static String getValue(String key, List<org.stagemonitor.configuration.source.ConfigurationSource> sources, String defaultValue) {
        for (org.stagemonitor.configuration.source.ConfigurationSource source : sources) {
            final String value = source.getValue(key);
            if (value != null) {
                return value;
            }
        }
        return defaultValue;
    }

    @Nonnull
    static String getActualLogFile(@Nullable String agentHome, String logFile) {
        if (logFile.equalsIgnoreCase(SYSTEM_OUT)) {
            return SYSTEM_OUT;
        }
        if (logFile.contains(AGENT_HOME_PLACEHOLDER)) {
            if (agentHome == null) {
                System.err.println("Could not resolve " + AGENT_HOME_PLACEHOLDER + ". Falling back to System.out.");
                return SYSTEM_OUT;
            } else {
                logFile = logFile.replace(AGENT_HOME_PLACEHOLDER, agentHome);
            }
        }
        logFile = new File(logFile).getAbsolutePath();
        final File logDir = new File(logFile).getParentFile();
        if (!logDir.exists()) {
            logDir.mkdir();
        }
        if (!logDir.canWrite()) {
            System.err.println("Log file " + logFile + " is not writable. Falling back to System.out.");
            return SYSTEM_OUT;
        }
        System.out.println("Writing Elastic APM logs to " + logFile);
        return logFile;
    }

    @Override
    protected String[] getSupportedTypes() {
        return null;
    }

    @Override
    public Configuration getConfiguration(LoggerContext loggerContext, String name, URI configLocation) {
        return getConfiguration();
    }

    @Override
    public Configuration getConfiguration(LoggerContext loggerContext, ConfigurationSource source) {
        return getConfiguration();
    }

    public Configuration getConfiguration() {
        ConfigurationBuilder<BuiltConfiguration> builder = newConfigurationBuilder();
        String logFile = getActualLogFile(ElasticApmAgent.getAgentHome(), getValue(LOG_FILE_KEY, sources, getValue(DEPRECATED_LOG_FILE_KEY, sources, DEFAULT_LOG_FILE)));
        Level level = Level.valueOf(getValue(LOG_LEVEL_KEY, sources, getValue(DEPRECATED_LOG_LEVEL_KEY, sources, Level.INFO.toString())));
        AppenderComponentBuilder appender = getAppender(builder, logFile);
        builder.setStatusLevel(Level.ERROR)
            .setConfigurationName("ElasticAPM")
            .add(appender)
            .add(builder.newRootLogger(level)
                .add(builder.newAppenderRef(appender.getName())));
        return builder.build();
    }

    private AppenderComponentBuilder getAppender(ConfigurationBuilder<BuiltConfiguration> builder, String logFile) {
        if (logFile.equals(SYSTEM_OUT)) {
            return builder.newAppender("Stdout", "CONSOLE")
                .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
                .add(builder
                    .newLayout("PatternLayout")
                    .addAttribute("pattern", "%d [%thread] %-5level %logger{36} - %msg%n"));
        } else {
            String serviceName = getValue(CoreConfiguration.SERVICE_NAME, sources, ServiceNameUtil.getDefaultServiceName());
            return builder.newAppender("rolling", "RollingFile")
                .addAttribute("fileName", logFile)
                .addAttribute("filePattern", logFile + "%i")
                .add(builder.newLayout("EcsLayout")
                    .addAttribute("serviceName", serviceName)
                    .addAttribute("eventDataset", serviceName + ".apm"))
                .addComponent(builder.newComponent("Policies")
                    .addComponent(builder.newComponent("SizeBasedTriggeringPolicy").addAttribute("size", "100M")))
                .addComponent(builder.newComponent("DefaultRolloverStrategy").addAttribute("max", 5));
        }
    }
}
