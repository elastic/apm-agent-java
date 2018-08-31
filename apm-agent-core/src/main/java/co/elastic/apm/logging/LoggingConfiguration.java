/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.logging;

import co.elastic.apm.bci.ElasticApmAgent;
import org.slf4j.event.Level;
import org.slf4j.impl.SimpleLogger;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.source.ConfigurationSource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
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

    private static final String SYSTEM_OUT = "System.out";
    private static final String LOG_LEVEL_KEY = "logging.log_level";
    private static final String LOG_FILE_KEY = "logging.log_file";
    private static final String DEFAULT_LOG_FILE = SYSTEM_OUT;

    private static final String LOGGING_CATEGORY = "Logging";
    private static final String AGENT_HOME_PLACEHOLDER = "_AGENT_HOME_";

    @SuppressWarnings("unused")
    public ConfigurationOption<Level> logLevel = ConfigurationOption.enumOption(Level.class)
        .key(LOG_LEVEL_KEY)
        .configurationCategory(LOGGING_CATEGORY)
        .description("Sets the logging level for the agent.\n" +
            "\n" +
            "This option is case-insensitive.")
        .dynamic(true)
        .addChangeListener(new ConfigurationOption.ChangeListener<Level>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, Level oldValue, Level newValue) {
                setLogLevel(newValue.toString());
            }
        })
        .buildWithDefault(Level.INFO);

    @SuppressWarnings("unused")
    public ConfigurationOption<String> logFile = ConfigurationOption.stringOption()
        .key(LOG_FILE_KEY)
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


    public static void init(List<ConfigurationSource> sources) {
        setLogLevel(getValue(LOG_LEVEL_KEY, sources, Level.INFO.toString()));
        setLogFileLocation(ElasticApmAgent.getAgentHome(), getValue(LOG_FILE_KEY, sources, DEFAULT_LOG_FILE));
    }

    /**
     * The ConfigurationRegistry uses and thereby initializes a logger,
     * so we can't use it here initialize the {@link ConfigurationOption}s in this class.
     */
    private static String getValue(String key, List<ConfigurationSource> sources, String defaultValue) {
        for (ConfigurationSource source : sources) {
            final String value = source.getValue(key);
            if (value != null) {
                return value;
            }
        }
        return defaultValue;
    }

    private static void setLogLevel(@Nullable String level) {
        System.setProperty(SimpleLogger.LOG_KEY_PREFIX + "co.elastic.apm", level != null ? level : Level.INFO.toString());
        System.setProperty(SimpleLogger.LOG_KEY_PREFIX + "co.elastic.apm.shaded", Level.WARN.toString());
        System.setProperty(SimpleLogger.SHOW_DATE_TIME_KEY, Boolean.TRUE.toString());
        System.setProperty(SimpleLogger.DATE_TIME_FORMAT_KEY, "yyyy-MM-dd HH:mm:ss.SSS");
    }

    private static void setLogFileLocation(@Nullable String agentHome, String logFile) {
        if (SYSTEM_OUT.equalsIgnoreCase(logFile)) {
            System.setProperty(SimpleLogger.LOG_FILE_KEY, SYSTEM_OUT);
        } else {
            System.setProperty(SimpleLogger.LOG_FILE_KEY, getActualLogFile(agentHome, logFile));
        }
    }

    @Nonnull
    private static String getActualLogFile(@Nullable String agentHome, String logFile) {
        if (logFile.contains(AGENT_HOME_PLACEHOLDER)) {
            if (agentHome == null) {
                System.err.println("Could not resolve " + AGENT_HOME_PLACEHOLDER + ". Falling back to System.out.");
                return SYSTEM_OUT;
            } else {
                logFile = logFile.replace(AGENT_HOME_PLACEHOLDER, agentHome);
            }
        }
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

}
