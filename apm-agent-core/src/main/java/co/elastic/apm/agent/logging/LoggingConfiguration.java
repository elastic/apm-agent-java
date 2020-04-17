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
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.event.Level;
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
 * (see {@link #getValue(String, List, String)}).
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
    static final String LOG_FILE_MAX_SIZE_KEY = "log_file_max_size";
    static final String DEFAULT_LOG_FILE = SYSTEM_OUT;

    private static final String LOGGING_CATEGORY = "Logging";
    public static final String AGENT_HOME_PLACEHOLDER = "_AGENT_HOME_";
    static final String DEPRECATED_LOG_LEVEL_KEY = "logging.log_level";
    static final String DEPRECATED_LOG_FILE_KEY = "logging.log_file";
    static final String DEFAULT_MAX_SIZE = "50mb";

    @SuppressWarnings("unused")
    public ConfigurationOption<Level> logLevel = ConfigurationOption.enumOption(Level.class)
        .key(LOG_LEVEL_KEY)
        .aliasKeys(DEPRECATED_LOG_LEVEL_KEY)
        .configurationCategory(LOGGING_CATEGORY)
        .description("Sets the logging level for the agent.\n" +
            "\n" +
            "This option is case-insensitive.")
        .dynamic(true)
        .addChangeListener(new ConfigurationOption.ChangeListener<Level>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, Level oldValue, Level newValue) {
                setLogLevel(newValue);
            }
        })
        .buildWithDefault(Level.INFO);

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
            "it's content is deleted when the application starts.")
        .dynamic(false)
        .buildWithDefault(DEFAULT_LOG_FILE);

    @SuppressWarnings("unused")
    public ConfigurationOption<ByteValue> logFileMaxSize = ByteValueConverter.byteOption()
        .key(LOG_FILE_MAX_SIZE_KEY)
        .configurationCategory(LOGGING_CATEGORY)
        .description("The max size of the log file.\n" +
            "To support sending up the log file to APM Server,\n" +
            "the agent always keeps one history file so that the max total log file size is twice this setting.")
        .dynamic(false)
        .buildWithDefault(ByteValue.of(DEFAULT_MAX_SIZE));

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

    public static void init(List<ConfigurationSource> sources) {
        Configurator.initialize(new Log4j2ConfigurationFactory(sources).getConfiguration());
    }

    public String getLogFile() {
        return logFile.get();
    }

    private static void setLogLevel(@Nullable Level level) {
        if (level == null) {
            level = Level.INFO;
        }
        Configurator.setRootLevel(org.apache.logging.log4j.Level.toLevel(level.toString(), org.apache.logging.log4j.Level.INFO));
    }

    public boolean isLogCorrelationEnabled() {
        return logCorrelationEnabled.get();
    }

}
