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

import co.elastic.apm.agent.configuration.converter.ByteValue;
import co.elastic.apm.agent.configuration.converter.ByteValueConverter;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.matcher.WildcardMatcherValueConverter;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.impl.Log4jContextFactory;
import org.apache.logging.log4j.core.selector.ContextSelector;
import org.apache.logging.log4j.spi.LoggerContextFactory;
import org.apache.logging.log4j.status.StatusLogger;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.converter.ListValueConverter;
import org.stagemonitor.configuration.converter.MapValueConverter;
import org.stagemonitor.configuration.converter.StringValueConverter;
import org.stagemonitor.configuration.source.ConfigurationSource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Defines configuration options related to logging.
 * <p>
 * This class is a bit special compared to other {@link ConfigurationOptionProvider}s,
 * because we have to make sure that wie initialize the logger before anyone calls
 * {@link LoggerFactory#getLogger(Class)}.
 * That's why we don't read the values from the {@link ConfigurationOption} fields but
 * iterate over the {@link ConfigurationSource}s manually to read the values
 * (see {@link Log4j2ConfigurationFactory#getValue}).
 * </p>
 * <p>
 * It still makes sense to extend {@link ConfigurationOptionProvider} and register this class as a service,
 * so that the documentation gets generated for the options in this class.
 * </p>
 */
public class LoggingConfiguration extends ConfigurationOptionProvider {

    public static final String SYSTEM_OUT = "System.out";
    static final String LOG_LEVEL_KEY = "log_level";
    static final String LOG_FILE_KEY = "log_file";
    static final String LOG_FILE_SIZE_KEY = "log_file_size";
    static final String DEFAULT_LOG_FILE = SYSTEM_OUT;

    private static final String LOGGING_CATEGORY = "Logging";
    public static final String AGENT_HOME_PLACEHOLDER = "_AGENT_HOME_";
    static final String DEPRECATED_LOG_LEVEL_KEY = "logging.log_level";
    static final String DEPRECATED_LOG_FILE_KEY = "logging.log_file";
    public static final String DEFAULT_MAX_SIZE = "50mb";
    static final String SHIP_AGENT_LOGS = "ship_agent_logs";
    static final String LOG_FORMAT_SOUT_KEY = "log_format_sout";
    public static final String LOG_FORMAT_FILE_KEY = "log_format_file";
    static final String INITIAL_LISTENERS_LEVEL = "log4j2.StatusLogger.level";
    static final String INITIAL_STATUS_LOGGER_LEVEL = "org.apache.logging.log4j.simplelog.StatusLogger.level";
    static final String DEFAULT_LISTENER_LEVEL = "Log4jDefaultStatusLevel";

    /**
     * We don't directly access most logging configuration values through the ConfigurationOption instance variables.
     * That would require the configuration registry to be initialized already.
     * However, the registry initializes logging by declaring a static final logger variable.
     * In order to break up the cyclic dependency and to not accidentally initialize logging before we had the chance to configure the logging,
     * we manually resolve these options.
     *
     * NOTE: on top of the above, this specific option should never be accessed through the ConfigurationOption as it
     * allows {@link LogLevel} that are effectively mapped to other values.
     *
     * See {@link Log4j2ConfigurationFactory#getValue}
     */
    @SuppressWarnings("unused")
    public ConfigurationOption<LogLevel> logLevel = ConfigurationOption.enumOption(LogLevel.class)
        .key(LOG_LEVEL_KEY)
        .aliasKeys(DEPRECATED_LOG_LEVEL_KEY)
        .configurationCategory(LOGGING_CATEGORY)
        .description("Sets the logging level for the agent.\n" +
            "This option is case-insensitive.\n" +
            "\n" +
            "NOTE: `CRITICAL` is a valid option, but it is mapped to `ERROR`; `WARN` and `WARNING` are equivalent; \n" +
            "`OFF` is only available since version 1.16.0")
        .dynamic(true)
        .addChangeListener(new ConfigurationOption.ChangeListener<LogLevel>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, LogLevel oldValue, LogLevel newValue) {
                newValue = mapLogLevel(newValue);
                setLogLevel(newValue);
            }
        })
        .buildWithDefault(LogLevel.INFO);

    /**
     * Maps a {@link LogLevel} that is supported by the agent to a value that is also supported by the underlying
     * logging framework.
     * @param original the agent-supported {@link LogLevel}
     * @return a {@link LogLevel} that is both supported by the agent and the underlying logging framework
     */
    @Nonnull
    static LogLevel mapLogLevel(LogLevel original) {
        LogLevel mapped = original;
        if (original == LogLevel.WARNING) {
            mapped = LogLevel.WARN;
        } else if (original == LogLevel.CRITICAL) {
            mapped = LogLevel.ERROR;
        }
        return mapped;
    }

    @SuppressWarnings("unused")
    public ConfigurationOption<String> logFile = ConfigurationOption.stringOption()
        .key(LOG_FILE_KEY)
        .aliasKeys(DEPRECATED_LOG_FILE_KEY)
        .configurationCategory(LOGGING_CATEGORY)
        .description("Sets the path of the agent logs.\n" +
            "The special value `_AGENT_HOME_` is a placeholder for the folder the elastic-apm-agent.jar is in.\n" +
            "Example: `_AGENT_HOME_/logs/elastic-apm.log`\n" +
            "\n" +
            "When set to the special value 'System.out',\n" +
            "the logs are sent to standard out.\n" +
            "\n" +
            "NOTE: When logging to a file,\n" +
            "the log will be formatted in new-line-delimited JSON.\n" +
            "When logging to std out, the log will be formatted as plain-text.")
        .dynamic(false)
        .buildWithDefault(DEFAULT_LOG_FILE);

    private final ConfigurationOption<Boolean> logCorrelationEnabled = ConfigurationOption.booleanOption()
        .key("enable_log_correlation")
        .configurationCategory(LOGGING_CATEGORY)
        .description("A boolean specifying if the agent should integrate into SLF4J's https://www.slf4j.org/api/org/slf4j/MDC.html[MDC] to enable trace-log correlation.\n" +
            "If set to `true`, the agent will set the `trace.id` and `transaction.id` for the currently active spans and transactions to the MDC.\n" +
            "Since version 1.16.0, the agent also adds `error.id` of captured error to the MDC just before the error message is logged.\n" +
            "See <<log-correlation>> for more details.\n" +
            "\n" +
            "NOTE: While it's allowed to enable this setting at runtime, you can't disable it without a restart.")
        .dynamic(true)
        .addValidator(new ConfigurationOption.Validator<Boolean>() {
            @Override
            public void assertValid(Boolean value) {
                if (logCorrelationEnabled != null && logCorrelationEnabled.get() && Boolean.FALSE.equals(value)) {
                    // the reason is that otherwise the MDC will not be cleared when disabling while a span is currently active
                    throw new IllegalArgumentException("Disabling the log correlation at runtime is not possible.");
                }
            }
        })
        .buildWithDefault(false);

    private final ConfigurationOption<LogEcsReformatting> logEcsReformatting = ConfigurationOption.enumOption(LogEcsReformatting.class)
        .key("log_ecs_reformatting")
        .configurationCategory(LOGGING_CATEGORY)
        .tags("added[1.22.0]", "experimental")
        .description("Specifying whether and how the agent should automatically reformat application logs \n" +
            "into {ecs-logging-ref}/index.html[ECS-compatible JSON], suitable for ingestion into Elasticsearch for \n" +
            "further Log analysis. This functionality is available for log4j1, log4j2 and Logback. \n" +
            "Once this option is enabled with any valid option, log correlation will be activated as well, " +
            "regardless of the <<config-enable-log-correlation,`enable_log_correlation`>> configuration. \n" +
            "\n" +
            "Available options:\n" +
            "\n" +
            " - OFF - application logs are not reformatted. \n" +
            " - SHADE - agent logs are reformatted and \"shade\" ECS-JSON-formatted logs are automatically created in \n" +
            "   addition to the original application logs. Shade logs will have the same name as the original logs, \n" +
            "   but with the \".ecs.json\" extension instead of the original extension. Destination directory for the \n" +
            "   shade logs can be configured through the <<config-log-ecs-reformatting-dir,`log_ecs_reformatting_dir`>> \n" +
            "   configuration. Shade logs do not inherit file-rollover strategy from the original logs. Instead, they \n" +
            "   use their own size-based rollover strategy according to the <<config-log-file-size, `log_file_size`>> \n" +
            "   configuration and while allowing maximum of two shade log files.\n" +
            " - REPLACE - similar to `SHADE`, but the original logs will not be written. This option is useful if \n" +
            "   you wish to maintain similar logging-related overhead, but write logs to a different location and/or \n" +
            "   with a different file extension.\n" +
            " - OVERRIDE - same log output is used, but in ECS-compatible JSON format instead of the original format. \n" +
            "\n" +
            "NOTE: while `SHADE` and `REPLACE` options are only relevant to file log appenders, the `OVERRIDE` option \n" +
            "is also valid for other appenders, like System out and console")
        .dynamic(true)
        .buildWithDefault(LogEcsReformatting.OFF);

    private final ConfigurationOption<Map<String, String>> logEcsReformattingAdditionalFields = ConfigurationOption
        .builder(new MapValueConverter<String, String>(StringValueConverter.INSTANCE, StringValueConverter.INSTANCE, "=", ","), Map.class)
        .key("log_ecs_reformatting_additional_fields")
        .tags("added[1.26.0]")
        .configurationCategory(LOGGING_CATEGORY)
        .description("A comma-separated list of key-value pairs that will be added as additional fields to all log events.\n " +
            "Takes the format `key=value[,key=value[,...]]`, for example: `key1=value1,key2=value2`.\n " +
            "Only relevant if <<config-log-ecs-reformatting,`log_ecs_reformatting`>> is set to any option other than `OFF`.\n")
        .dynamic(false)
        .buildWithDefault(Collections.<String, String>emptyMap());

    private final ConfigurationOption<List<WildcardMatcher>> logEcsFormatterAllowList = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("log_ecs_formatter_allow_list")
        .configurationCategory(LOGGING_CATEGORY)
        .description("Only formatters that match an item on this list will be automatically reformatted to ECS when \n" +
            "<<config-log-ecs-reformatting,`log_ecs_reformatting`>> is set to any option other than `OFF`. \n" +
            "A formatter is the logging-framework-specific entity that is responsible for the formatting \n" +
            "of log events. For example, in log4j it would be a `Layout` implementation, whereas in Logback it would \n" +
            "be an `Encoder` implementation. \n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION
        )
        .dynamic(false)
        .buildWithDefault(Arrays.asList(
            WildcardMatcher.valueOf("*PatternLayout*"),
            WildcardMatcher.valueOf("org.apache.log4j.SimpleLayout"),
            WildcardMatcher.valueOf("ch.qos.logback.core.encoder.EchoEncoder")
        ));

    private final ConfigurationOption<String> logEcsFormattingDestinationDir = ConfigurationOption.stringOption()
        .key("log_ecs_reformatting_dir")
        .configurationCategory(LOGGING_CATEGORY)
        .description("If <<config-log-ecs-reformatting,`log_ecs_reformatting`>> is set to `SHADE` or `REPLACE`, \n" +
            "the shade log files will be written alongside the original logs in the same directory by default. \n" +
            "Use this configuration in order to write the shade logs into an alternative destination. Omitting this \n" +
            "config or setting it to an empty string will restore the default behavior. If relative path is used, \n" +
            "this path will be used relative to the original logs directory.")
        .dynamic(false)
        .buildWithDefault("");

    @SuppressWarnings("unused")
    public ConfigurationOption<ByteValue> logFileSize = ByteValueConverter.byteOption()
        .key(LOG_FILE_SIZE_KEY)
        .configurationCategory(LOGGING_CATEGORY)
        .description("The size of the log file.\n" +
            "\n" +
            //"To support <<config-ship-agent-logs,shipping the logs>> to APM Server,\n" +
            "The agent always keeps one history file so that the max total log file size is twice the value of this setting.\n")
        .dynamic(false)
        .tags("added[1.17.0]")
        .buildWithDefault(ByteValue.of(DEFAULT_MAX_SIZE));

    private final ConfigurationOption<Boolean> shipAgentLogs = ConfigurationOption.booleanOption()
        .key(SHIP_AGENT_LOGS)
        .configurationCategory(LOGGING_CATEGORY)
        .description("This helps you to centralize your agent logs by automatically sending them to APM Server (requires APM Server 7.9+).\n" +
            "Use the Kibana Logs App to see the logs from all of your agents.\n" +
            "\n" +
            "If <<config-log-file,`log_file`>> is set to a real file location (as opposed to `System.out`),\n" +
            "this file will be shipped to the APM Server by the agent.\n" +
            "Note that <<config-log-format-file,`log_format_file`>> needs to be set to `JSON` when this option is enabled.\n" +
            "\n" +
            "If APM Server is temporarily not available, the agent will resume sending where it left off as soon as the server is back up again.\n" +
            "The amount of logs that can be buffered is at least <<config-log-file-size,`log_file_size`>>.\n" +
            "If the application crashes or APM Server is not available when shutting down,\n" +
            "the agent will resume shipping the log file when the application restarts.\n" +
            "\n" +
            "Resume on restart does not work when the log is inside an ephemeral container.\n" +
            "Consider mounting the log file to the host or use Filebeat if you need the extra reliability in this case.\n" +
            "\n" +
            "If <<config-log-file,`log_file`>> is set to `System.out`,\n" +
            "the agent will additionally log into a temp file which is then sent to APM Server.\n" +
            "This log's size is determined by <<config-log-file-size,`log_file_size`>> and will be deleted on shutdown.\n" +
            "This means that logs that could not be sent before the application terminates are lost.")
        .dynamic(false)
        .tags("added[not officially added yet]", "internal")
        .buildWithDefault(false);

    @SuppressWarnings("unused")
    public ConfigurationOption<LogFormat> logFormatSout = ConfigurationOption.enumOption(LogFormat.class)
        .key(LOG_FORMAT_SOUT_KEY)
        .configurationCategory(LOGGING_CATEGORY)
        .description("Defines the log format when logging to `System.out`.\n" +
            "\n" +
            "When set to `JSON`, the agent will format the logs in an https://github.com/elastic/ecs-logging-java[ECS-compliant JSON format]\n" +
            "where each log event is serialized as a single line.")
        .tags("added[1.17.0]")
        .buildWithDefault(LogFormat.PLAIN_TEXT);

    @SuppressWarnings("unused")
    public ConfigurationOption<LogFormat> logFormatFile = ConfigurationOption.enumOption(LogFormat.class)
        .key(LOG_FORMAT_FILE_KEY)
        .configurationCategory(LOGGING_CATEGORY)
        .description("Defines the log format when logging to a file.\n" +
            "\n" +
            "When set to `JSON`, the agent will format the logs in an https://github.com/elastic/ecs-logging-java[ECS-compliant JSON format]\n" +
            "where each log event is serialized as a single line.\n"
            //+ "\n" +
            //"If <<config-ship-agent-logs,`ship_agent_logs`>> is enabled,\n" +
            //"the value has to be `JSON`."
        )
        .tags("added[1.17.0]")
        .buildWithDefault(LogFormat.PLAIN_TEXT);

    public static void init(List<ConfigurationSource> sources, String ephemeralId) {
        // The initialization of log4j may produce errors if the traced application uses log4j settings (for
        // example - through file in the classpath or System properties) that configures specific properties for
        // loading custom classes by name, which may not be found within the agent ClassLoader's classpath.
        // All handled Exceptions should not prevent us from using log4j further, as the system falls back to a default
        // which we expect anyway. We take a calculated risk of ignoring such errors only through initialization time,
        // assuming that errors that will make the logging system non-usable won't be handled.
        String initialListenersLevel = System.setProperty(INITIAL_LISTENERS_LEVEL, "OFF");
        String initialStatusLoggerLevel = System.setProperty(INITIAL_STATUS_LOGGER_LEVEL, "OFF");
        String defaultListenerLevel = System.setProperty(DEFAULT_LISTENER_LEVEL, "OFF");
        try {
            // org.apache.logging.log4j.core.config.ConfigurationFactory is a singleton that allows overriding its instance
            // through setConfigurationFactory. This API is not considered thread safe, but since we do it so early on when
            // there is only the main thread it should be OK. Once we override the default factory instance, any logger
            // created thereafter will be configured through our custom factory, regardless of its context.
            // Initializing only per context (the caller class loader by default, can be changed to thread or other), for
            // example through org.apache.logging.log4j.core.config.Configurator, means that loggers in non-initialized
            // contexts will either get the app-configuration for log4j, if such exists, or none.
            ConfigurationFactory.setConfigurationFactory(new Log4j2ConfigurationFactory(sources, ephemeralId));
            LoggerFactory.initialize(new Log4jLoggerFactoryBridge());
        } catch (Throwable throwable) {
            System.err.println("[elastic-apm-agent] ERROR Failure during initialization of agent's log4j system: " + throwable.getMessage());
        } finally {
            restoreSystemProperty(INITIAL_LISTENERS_LEVEL, initialListenersLevel);
            restoreSystemProperty(INITIAL_STATUS_LOGGER_LEVEL, initialStatusLoggerLevel);
            restoreSystemProperty(DEFAULT_LISTENER_LEVEL, defaultListenerLevel);
            StatusLogger.getLogger().setLevel(Level.ERROR);
        }
    }

    private static void restoreSystemProperty(String key, @Nullable String originalValue) {
        if (originalValue != null) {
            System.setProperty(key, originalValue);
        } else {
            System.clearProperty(key);
        }
    }

    public String getLogFile() {
        return logFile.get();
    }

    private static void setLogLevel(@Nullable LogLevel level) {
        if (level == null) {
            level = LogLevel.INFO;
        }
        Level log4jLevel = Level.toLevel(level.toString(), Level.INFO);
        LoggerContextFactory contextFactory = LogManager.getFactory();
        if (contextFactory instanceof Log4jContextFactory) {
            final ContextSelector selector = ((Log4jContextFactory) contextFactory).getSelector();
            for (LoggerContext loggerContext : selector.getLoggerContexts()) {
                // Taken from org.apache.logging.log4j.core.config.Configurator#setRootLevel()
                final LoggerConfig loggerConfig = loggerContext.getConfiguration().getRootLogger();
                if (!loggerConfig.getLevel().equals(log4jLevel)) {
                    loggerConfig.setLevel(log4jLevel);
                    loggerContext.updateLoggers();
                }
            }
        } else {
            // it should be safe to obtain a logger here
            LoggerFactory.getLogger(LoggingConfiguration.class).warn("Unexpected type of LoggerContextFactory - {}, " +
                "cannot update logging level", contextFactory);
        }

        // Setting the root level resets all the other loggers that may have been configured, which overrides
        // configuration provided by the configuration files in the classpath. While the JSON schema validator is only
        // used for testing and is not shipped, this is the most convenient solution to avoid verbosity here.
        Configurator.setLevel("com.networknt.schema", org.apache.logging.log4j.Level.WARN);
    }

    public boolean isLogCorrelationEnabled() {
        // Enabling automatic ECS-reformatting implicitly enables log correlation
        return logCorrelationEnabled.get() || getLogEcsReformatting() != LogEcsReformatting.OFF;
    }

    public LogEcsReformatting getLogEcsReformatting() {
        return logEcsReformatting.get();
    }

    public Map<String, String> getLogEcsReformattingAdditionalFields() {
        return logEcsReformattingAdditionalFields.get();
    }

    public List<WildcardMatcher> getLogEcsFormatterAllowList() {
        return logEcsFormatterAllowList.get();
    }

    @Nullable
    public String getLogEcsFormattingDestinationDir() {
        String logShadingDestDir = logEcsFormattingDestinationDir.get().trim();
        return (logShadingDestDir.isEmpty()) ? null : logShadingDestDir;
    }

    public long getLogFileSize() {
        return logFileSize.get().getBytes();
    }

    public boolean isShipAgentLogs() {
        return shipAgentLogs.get();
    }

    public LogFormat getLogFormatFile() {
        return logFormatFile.get();
    }
}
