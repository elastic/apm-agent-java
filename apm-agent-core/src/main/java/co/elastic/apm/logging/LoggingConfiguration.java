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

import org.slf4j.event.Level;
import org.slf4j.impl.SimpleLogger;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.source.ConfigurationSource;

import java.util.List;

public class LoggingConfiguration extends ConfigurationOptionProvider {

    private static final String LOGGING_CATEGORY = "Logging";
    private static final String LOG_LEVEL_KEY = "logging.log_level";

    public ConfigurationOption<Level> logLevel = ConfigurationOption.enumOption(Level.class)
        .key(LOG_LEVEL_KEY)
        .configurationCategory(LOGGING_CATEGORY)
        .description("Sets the logging level for the agent.")
        .dynamic(true)
        .addChangeListener(new ConfigurationOption.ChangeListener<Level>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, Level oldValue, Level newValue) {
                setLogLevel(newValue.toString());
            }
        })
        .buildWithDefault(Level.INFO);

    public static void init(List<ConfigurationSource> sources) {
        final String level = getValue(LOG_LEVEL_KEY, sources, Level.INFO.toString());
        LoggingConfiguration.setLogLevel(level);
    }

    public static String getValue(String key, List<ConfigurationSource> sources, String defaultValue) {
        for (ConfigurationSource source : sources) {
            final String value = source.getValue(key);
            if (value != null) {
                return value;
            }
        }
        return defaultValue;
    }

    private static void setLogLevel(String level) {
        System.setProperty(SimpleLogger.LOG_FILE_KEY, "System.out");
        System.setProperty(SimpleLogger.LOG_KEY_PREFIX + "co.elastic.apm", level != null ? level : Level.INFO.toString());
        System.setProperty(SimpleLogger.LOG_KEY_PREFIX + "co.elastic.apm.shaded", Level.WARN.toString());
    }
}
