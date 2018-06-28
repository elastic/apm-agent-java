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

import org.junit.jupiter.api.Test;
import org.slf4j.impl.SimpleLogger;
import org.stagemonitor.configuration.source.SimpleSource;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingConfigurationTest {

    @Test
    void testSetLogLevel() {
        LoggingConfiguration.init(Collections.singletonList(new SimpleSource().add("logging.log_level", "DEBUG")));
        assertThat(System.getProperty(SimpleLogger.LOG_KEY_PREFIX + "co.elastic.apm")).isEqualTo("DEBUG");
    }

    @Test
    void testSetLogFileInvalid() {
        LoggingConfiguration.init(Collections.singletonList(new SimpleSource().add("logging.log_file", "/this/does/not/exist")));
        assertThat(System.getProperty(SimpleLogger.LOG_FILE_KEY)).isEqualTo("System.out");
    }

    @Test
    void testSetLogFile() {
        final String logFile = System.getProperty("java.io.tmpdir") + "apm.log";
        LoggingConfiguration.init(Collections.singletonList(new SimpleSource().add("logging.log_file", logFile)));
        assertThat(System.getProperty(SimpleLogger.LOG_FILE_KEY)).isEqualTo(logFile);
    }

    @Test
    void testSetLogFileAgentHomeCantBeResolvedInTests() {
        // re-configuring the log file does not actually work,
        // so we can't verify the content of the log file
        LoggingConfiguration.init(Collections.singletonList(new SimpleSource().add("logging.log_file", "_AGENT_HOME_/logs/apm.log")));
        assertThat(System.getProperty(SimpleLogger.LOG_FILE_KEY)).isEqualTo("System.out");
    }
}
