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

import co.elastic.apm.agent.configuration.converter.ByteValue;
import co.elastic.apm.agent.configuration.converter.ByteValueConverter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.status.StatusLogger;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.source.ConfigurationSource;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Defines configuration options related to logging.
 * <p>
 * This class is a bit special compared to other {@link ConfigurationOptionProvider}s,
 * because we have to make sure that wie initialize the logger before anyone calls
 * {@link org.slf4j.LoggerFactory#getLogger(Class)}.
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
    static final String DEFAULT_MAX_SIZE = "50mb";
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
     * See {@link Log4j2ConfigurationFactory#getValue}
     */
    @SuppressWarnings("unused")
    public ConfigurationOption<LogLevel> logLevel = ConfigurationOption.enumOption(LogLevel.class)
        .key(LOG_LEVEL_KEY)
        .aliasKeys(DEPRECATED_LOG_LEVEL_KEY)
        .configurationCategory(LOGGING_CATEGORY)
        .description("Sets the logging level for the agent.\n" +
            "\n" +
            "This option is case-insensitive.")
        .dynamic(true)
        .addChangeListener(new ConfigurationOption.ChangeListener<LogLevel>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, LogLevel oldValue, LogLevel newValue) {
                setLogLevel(newValue);
            }
        })
        .buildWithDefault(LogLevel.INFO);

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
                if (logCorrelationEnabled != null && isLogCorrelationEnabled() && Boolean.FALSE.equals(value)) {
                    // the reason is that otherwise the MDC will not be cleared when disabling while a span is currently active
                    throw new IllegalArgumentException("Disabling the log correlation at runtime is not possible.");
                }
            }
        })
        .buildWithDefault(false);

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
        // loading classes by name. Since we shade our usage of log4j, such non-shaded classes may not (and should not)
        // be found on the classpath.
        // All handled Exceptions should not prevent us from using log4j further, as the system falls back to a default
        // which we expect anyway. We take a calculated risk of ignoring such errors only through initialization time,
        // assuming that errors that will make the logging system non-usable won't be handled.
        String initialListenersLevel = System.setProperty(INITIAL_LISTENERS_LEVEL, "OFF");
        String initialStatusLoggerLevel = System.setProperty(INITIAL_STATUS_LOGGER_LEVEL, "OFF");
        String defaultListenerLevel = System.setProperty(DEFAULT_LISTENER_LEVEL, "OFF");
        try {
            Configurator.initialize(new Log4j2ConfigurationFactory(sources, ephemeralId).getConfiguration());
        } catch (Throwable throwable) {
            System.err.println("Failure during initialization of agent's log4j system: " + throwable.getMessage());
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
        Configurator.setRootLevel(org.apache.logging.log4j.Level.toLevel(level.toString(), org.apache.logging.log4j.Level.INFO));
    }

    public boolean isLogCorrelationEnabled() {
        return logCorrelationEnabled.get();
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
