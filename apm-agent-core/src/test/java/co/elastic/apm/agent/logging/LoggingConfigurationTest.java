/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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

import org.junit.jupiter.api.Test;
import org.slf4j.impl.SimpleLogger;
import org.stagemonitor.configuration.source.SimpleSource;

import java.io.File;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingConfigurationTest {

    @Test
    void testSetLogLevel() {
        LoggingConfiguration.init(Collections.singletonList(new SimpleSource().add("log_level", "DEBUG")));
        assertThat(System.getProperty(SimpleLogger.LOG_KEY_PREFIX + "co.elastic.apm")).isEqualTo("DEBUG");
    }

    @Test
    void testSetLogFileInvalid() {
        LoggingConfiguration.init(Collections.singletonList(new SimpleSource().add("log_file", "/this/does/not/exist")));
        assertThat(System.getProperty(SimpleLogger.LOG_FILE_KEY)).isEqualTo("System.out");
    }

    @Test
    void testSetLogFile() {
        final String logFile = "apm.log";
        LoggingConfiguration.init(Collections.singletonList(new SimpleSource().add("log_file", logFile)));
        assertThat(System.getProperty(SimpleLogger.LOG_FILE_KEY)).isEqualTo(new File(logFile).getAbsolutePath());
    }

    @Test
    void testSetLogFileAgentHomeCantBeResolvedInTests() {
        // re-configuring the log file does not actually work,
        // so we can't verify the content of the log file
        LoggingConfiguration.init(Collections.singletonList(new SimpleSource().add("log_file", "_AGENT_HOME_/logs/apm.log")));
        assertThat(System.getProperty(SimpleLogger.LOG_FILE_KEY)).isEqualTo("System.out");
    }
}
