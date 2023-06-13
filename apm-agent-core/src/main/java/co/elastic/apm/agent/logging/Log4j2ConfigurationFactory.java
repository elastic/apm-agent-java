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
package co.elastic.apm.agent.logging;

import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.common.util.SystemStandardOutputLogger;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.ServiceInfo;
import co.elastic.apm.agent.tracer.configuration.ByteValue;
import co.elastic.apm.agent.report.ApmServerReporter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.AppenderRefComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.RootLoggerComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.converter.EnumValueConverter;

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
import static co.elastic.apm.agent.logging.LoggingConfiguration.LOG_FORMAT_FILE_KEY;
import static co.elastic.apm.agent.logging.LoggingConfiguration.LOG_FORMAT_SOUT_KEY;
import static co.elastic.apm.agent.logging.LoggingConfiguration.LOG_LEVEL_KEY;
import static co.elastic.apm.agent.logging.LoggingConfiguration.SYSTEM_OUT;

public class Log4j2ConfigurationFactory extends ConfigurationFactory {

    public static final String APM_SERVER_PLUGIN_NAME = "ApmServer";
    public static final String APM_SERVER_FILTER_PLUGIN_NAME = "ApmServerFilter";

    private final List<org.stagemonitor.configuration.source.ConfigurationSource> sources;
    private final String ephemeralId;

    @Nullable
    private Configuration config;

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
                SystemStandardOutputLogger.stdErrWarn("Could not resolve " + AGENT_HOME_PLACEHOLDER + ". Falling back to System.out.");
                return SYSTEM_OUT;
            } else {
                logFile = logFile.replace(AGENT_HOME_PLACEHOLDER, agentHome);
            }
        }

        boolean canWrite;
        try {
            logFile = new File(logFile).getAbsolutePath();
            final File logDir = new File(logFile).getParentFile();

            // already privileged, thus will work as expected when security manager is enabled and agent permission set
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            canWrite = logDir.canWrite();
        } catch (SecurityException e) {
            canWrite = false;
        }

        if (!canWrite) {
            SystemStandardOutputLogger.stdErrWarn("Log file " + logFile + " is not writable. Falling back to System.out.");
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

    Configuration getConfiguration() {
        if(config != null){
            return config;
        }

        ConfigurationBuilder<BuiltConfiguration> builder = newConfigurationBuilder();
        builder.setStatusLevel(Level.ERROR)
            .setConfigurationName("ElasticAPM")
            .setShutdownHook("disable");

        Level level = getLogLevel();
        RootLoggerComponentBuilder rootLogger = builder.newRootLogger(level);
        createAppenders(builder, rootLogger);
        builder.add(rootLogger);
        config = builder.build();
        return config;
    }

    private Level getLogLevel() {
        String rawLogLevelValue = getValue(LOG_LEVEL_KEY, sources, getValue(DEPRECATED_LOG_LEVEL_KEY, sources, Level.INFO.toString()));
        LogLevel logLevel = LoggingConfiguration.mapLogLevel(new EnumValueConverter<>(LogLevel.class).convert(rawLogLevelValue));
        return Level.valueOf(logLevel.toString());
    }

    private void createAppenders(ConfigurationBuilder<BuiltConfiguration> builder, RootLoggerComponentBuilder rootLogger) {
        String logFile = getActualLogFile(ElasticApmAgent.getAgentHome(), getValue(LOG_FILE_KEY, sources, getValue(DEPRECATED_LOG_FILE_KEY, sources, DEFAULT_LOG_FILE)));
        if (logFile.equals(SYSTEM_OUT)) {
            rootLogger.add(createConsoleAppender(builder));
        } else {
            rootLogger.add(createFileAppender(builder, logFile, createLayout(builder, getFileLogFormat())));
        }
        rootLogger.add(createSendingAppender(builder));
    }

    private AppenderRefComponentBuilder createConsoleAppender(ConfigurationBuilder<BuiltConfiguration> builder) {
        String appenderName = "Stdout";
        AppenderComponentBuilder appender = builder.newAppender(appenderName, "CONSOLE")
            .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
            .add(createLayout(builder, getSoutLogFormat()));

        builder.add(appender);
        return builder.newAppenderRef(appender.getName());
    }

    private LayoutComponentBuilder createLayout(ConfigurationBuilder<BuiltConfiguration> builder, LogFormat logFormat) {
        if (logFormat == LogFormat.PLAIN_TEXT) {
            return builder
                .newLayout("PatternLayout")
                .addAttribute("pattern", "%d [%thread] %-5level %logger{36} - %msg{nolookups}%n");
        } else {
            String serviceName = getValue(CoreConfiguration.SERVICE_NAME, sources, ServiceInfo.autoDetected().getServiceName());
            return builder.newLayout("EcsLayout")
                .addAttribute("eventDataset", serviceName + ".apm-agent");
        }
    }

    private LogFormat getSoutLogFormat() {
        return new EnumValueConverter<>(LogFormat.class).convert(getValue(LOG_FORMAT_SOUT_KEY, sources, LogFormat.PLAIN_TEXT.toString()));
    }

    private LogFormat getFileLogFormat() {
        return new EnumValueConverter<>(LogFormat.class).convert(getValue(LOG_FORMAT_FILE_KEY, sources, LogFormat.PLAIN_TEXT.toString()));
    }

    private AppenderRefComponentBuilder createFileAppender(ConfigurationBuilder<BuiltConfiguration> builder, String logFile, LayoutComponentBuilder layout) {
        ByteValue size = ByteValue.of(getValue("log_file_size", sources, LoggingConfiguration.DEFAULT_MAX_SIZE));

        AppenderComponentBuilder appender = builder.newAppender("rolling", "RollingFile")
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

        builder.add(appender);
        return builder.newAppenderRef(appender.getName());
    }

    private AppenderRefComponentBuilder createSendingAppender(ConfigurationBuilder<BuiltConfiguration> builder) {
        AppenderComponentBuilder appender = builder.newAppender("apm-server", APM_SERVER_PLUGIN_NAME)
            .add(createLayout(builder, LogFormat.JSON));

        builder.add(appender);

        // unlike other appenders, we have to filter on the appender ref to prevent recursive logger invocation
        AppenderRefComponentBuilder appenderRef = builder.newAppenderRef(appender.getName());

        appenderRef.add(builder
            .newFilter(APM_SERVER_FILTER_PLUGIN_NAME, Filter.Result.DENY, Filter.Result.NEUTRAL)
            // we have to ignore logging from the 'reporter' package to prevent recursive calls to log appenders
            // for example a full send queue log message makes no sense to be also added to the (already filled) event
            // queue, so we just ignore those. That means debugging communication or dropped events will require to
            // use agent log file for proper investigation.
            .addAttribute("ignoreLoggerPrefix", ApmServerReporter.class.getPackage().getName() + "."));

        return appenderRef;
    }
}
