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
import co.elastic.apm.agent.configuration.converter.ByteValue;
import co.elastic.logging.log4j2.EcsLayout;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.RootLoggerComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.converter.EnumValueConverter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static co.elastic.apm.agent.logging.LoggingConfiguration.AGENT_HOME_PLACEHOLDER;
import static co.elastic.apm.agent.logging.LoggingConfiguration.DEFAULT_LOG_FILE;
import static co.elastic.apm.agent.logging.LoggingConfiguration.DEPRECATED_LOG_FILE_KEY;
import static co.elastic.apm.agent.logging.LoggingConfiguration.DEPRECATED_LOG_LEVEL_KEY;
import static co.elastic.apm.agent.logging.LoggingConfiguration.LOG_FILE_KEY;
import static co.elastic.apm.agent.logging.LoggingConfiguration.LOG_FORMAT_FILE_KEY;
import static co.elastic.apm.agent.logging.LoggingConfiguration.LOG_FORMAT_SOUT_KEY;
import static co.elastic.apm.agent.logging.LoggingConfiguration.LOG_LEVEL_KEY;
import static co.elastic.apm.agent.logging.LoggingConfiguration.SHIP_AGENT_LOGS;
import static co.elastic.apm.agent.logging.LoggingConfiguration.SYSTEM_OUT;

public class Log4j2ConfigurationFactory extends ConfigurationFactory {

    static {
        // We have to add this so that log4j can both load its internal plugins and the ECS plugin
        // That's because we have to omit the plugin descriptor file Log4j2Plugins.dat because there's no good way to shade the binary content
        PluginManager.addPackage(EcsLayout.class.getPackage().getName());
        PluginManager.addPackage(LoggerContext.class.getPackage().getName());
    }

    private final List<org.stagemonitor.configuration.source.ConfigurationSource> sources;
    private final String ephemeralId;

    public Log4j2ConfigurationFactory(List<org.stagemonitor.configuration.source.ConfigurationSource> sources, String ephemeralId) {
        this.sources = sources;
        this.ephemeralId = ephemeralId;
    }

    /**
     * The ConfigurationRegistry uses and thereby initializes a logger,
     * so we can't use it here initialize the {@link ConfigurationOption}s in this class.
     */
    static String getValue(String key, List<org.stagemonitor.configuration.source.ConfigurationSource> sources, String defaultValue) {
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
                System.err.println("[elastic-apm-agent] WARN - Could not resolve " + AGENT_HOME_PLACEHOLDER + ". Falling back to System.out.");
                return SYSTEM_OUT;
            } else {
                logFile = logFile.replace(AGENT_HOME_PLACEHOLDER, agentHome);
            }
        }
        logFile = new File(logFile).getAbsolutePath();
        final File logDir = new File(logFile).getParentFile();
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        if (!logDir.canWrite()) {
            System.err.println("[elastic-apm-agent] WARN - Log file " + logFile + " is not writable. Falling back to System.out.");
            return SYSTEM_OUT;
        }
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
        builder.setStatusLevel(Level.ERROR)
            .setConfigurationName("ElasticAPM");

        Level level = Level.valueOf(getValue(LOG_LEVEL_KEY, sources, getValue(DEPRECATED_LOG_LEVEL_KEY, sources, Level.INFO.toString())));
        RootLoggerComponentBuilder rootLogger = builder.newRootLogger(level);
        List<AppenderComponentBuilder> appenders = createAppenders(builder);
        for (AppenderComponentBuilder appender : appenders) {
            rootLogger.add(builder.newAppenderRef(appender.getName()));
        }
        builder.add(rootLogger);
        return builder.build();
    }

    private List<AppenderComponentBuilder> createAppenders(ConfigurationBuilder<BuiltConfiguration> builder) {
        List<AppenderComponentBuilder> appenders = new ArrayList<>();
        String logFile = getActualLogFile(ElasticApmAgent.getAgentHome(), getValue(LOG_FILE_KEY, sources, getValue(DEPRECATED_LOG_FILE_KEY, sources, DEFAULT_LOG_FILE)));
        if (logFile.equals(SYSTEM_OUT)) {
            appenders.add(createConsoleAppender(builder));
            if (Boolean.parseBoolean(getValue(SHIP_AGENT_LOGS, sources, Boolean.TRUE.toString()))) {
                File tempLog = getTempLogFile(ephemeralId);
                tempLog.deleteOnExit();
                File rotatedTempLog = new File(tempLog + ".1");
                rotatedTempLog.deleteOnExit();
                appenders.add(createFileAppender(builder, tempLog.getAbsolutePath(), createLayout(builder, LogFormat.JSON)));
            }
        } else {
            appenders.add(createFileAppender(builder, logFile, createLayout(builder, getFileLogFormat())));
        }
        for (AppenderComponentBuilder appender : appenders) {
            builder.add(appender);
        }
        return appenders;
    }

    public static File getTempLogFile(String ephemeralId) {
        return new File(System.getProperty("java.io.tmpdir"), "elasticapm-java-" + ephemeralId + ".log.json");
    }

    private AppenderComponentBuilder createConsoleAppender(ConfigurationBuilder<BuiltConfiguration> builder) {
        return builder.newAppender("Stdout", "CONSOLE")
            .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
            .add(createLayout(builder, getSoutLogFormat()));
    }

    private LayoutComponentBuilder createLayout(ConfigurationBuilder<BuiltConfiguration> builder, LogFormat logFormat) {
        if (logFormat == LogFormat.PLAIN_TEXT) {
            return builder
                    .newLayout("PatternLayout")
                    .addAttribute("pattern", "%d [%thread] %-5level %logger{36} - %msg%n");
        } else {
            String serviceName = getValue(CoreConfiguration.SERVICE_NAME, sources, ServiceNameUtil.getDefaultServiceName());
            return builder.newLayout("EcsLayout")
                .addAttribute("eventDataset", serviceName + ".apm");
        }
    }

    private LogFormat getSoutLogFormat() {
        return new EnumValueConverter<>(LogFormat.class).convert(getValue(LOG_FORMAT_SOUT_KEY, sources, LogFormat.PLAIN_TEXT.toString()));
    }

    private LogFormat getFileLogFormat() {
        return new EnumValueConverter<>(LogFormat.class).convert(getValue(LOG_FORMAT_FILE_KEY, sources, LogFormat.JSON.toString()));
    }

    private AppenderComponentBuilder createFileAppender(ConfigurationBuilder<BuiltConfiguration> builder, String logFile, LayoutComponentBuilder layout) {
        ByteValue size = ByteValue.of(getValue("log_file_max_size", sources, LoggingConfiguration.DEFAULT_MAX_SIZE));
        return builder.newAppender("rolling", "RollingFile")
            .addAttribute("fileName", logFile)
            .addAttribute("filePattern", logFile + ".%i")
            .add(layout)
            .addComponent(builder.newComponent("Policies")
                .addComponent(builder.newComponent("SizeBasedTriggeringPolicy").addAttribute("size", size.getBytes() + "B")))
            // Always keep exactly one history file.
            // This is needed to ensure that the rest of the file can be sent when its rotated.
            // Storing multiple history files would give the false impression that, for example,
            // when currently reading from apm.log2, the reading would continue from apm.log1.
            // This is not the case, when apm.log2 is fully read, the reading will continue from apm.log.
            // That is because we don't want to require the reader having to know the file name pattern of the rotated file.
            .addComponent(builder.newComponent("DefaultRolloverStrategy").addAttribute("max", 1));
    }
}
