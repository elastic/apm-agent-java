/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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

import co.elastic.logging.log4j2.EcsLayout;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.stagemonitor.configuration.source.AbstractConfigurationSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;


class Log4j2ConfigurationFactoryTest {

    private Appender appender;

    @AfterEach
    public void stopRollingAppender() {
        if (appender != null) {
            appender.stop();
        }
    }

    @Test
    void testLogFileJson(@TempDir Path tempDir) {
        String logFile = tempDir.resolve("agent.json").toString();
        Configuration configuration = getLogConfig(Map.of("log_file", logFile, "log_format_file", "json"));

        assertThat(configuration.getAppenders().values()).hasSize(1);
        appender = configuration.getAppenders().values().iterator().next();

        assertThat(appender).isInstanceOf(RollingFileAppender.class);
        assertThat(((RollingFileAppender) appender).getFileName()).isEqualTo(logFile);
        assertThat(appender.getLayout()).isInstanceOf(EcsLayout.class);
    }

    @Test
    void testLogFilePlainText(@TempDir Path tempDir) {
        String logFile = tempDir.resolve("agent.log").toString();
        Configuration configuration = getLogConfig(Map.of("log_file", logFile));

        assertThat(configuration.getAppenders().values()).hasSize(1);
        appender = configuration.getAppenders().values().iterator().next();

        assertThat(appender).isInstanceOf(RollingFileAppender.class);
        assertThat(((RollingFileAppender) appender).getFileName()).isEqualTo(logFile);
        assertThat(appender.getLayout()).isInstanceOf(PatternLayout.class);
    }

    @Test
    void testSoutPlainText() {
        Configuration configuration = getLogConfig(Map.of("ship_agent_logs", "false"));

        assertThat(configuration.getAppenders().values()).hasSize(1);
        Appender appender = configuration.getAppenders().values().iterator().next();

        assertThat(appender).isInstanceOf(ConsoleAppender.class);
        assertThat(appender.getLayout()).isInstanceOf(PatternLayout.class);
    }

    @Test
    void testSoutJson() {
        Configuration configuration = getLogConfig(Map.of("ship_agent_logs", "false", "log_format_sout", "JSON"));

        assertThat(configuration.getAppenders().values()).hasSize(1);
        Appender appender = configuration.getAppenders().values().iterator().next();

        assertThat(appender).isInstanceOf(ConsoleAppender.class);
        assertThat(appender.getLayout()).isInstanceOf(EcsLayout.class);
    }

    @Test
    void testSoutPlainTextTempJson() {
        Configuration configuration = getLogConfig(Map.of());

        assertThat(configuration.getAppenders().values()).hasSize(2);
        Optional<ConsoleAppender> consoleAppender = configuration.getAppenders().values().stream()
            .filter(ConsoleAppender.class::isInstance)
            .map(ConsoleAppender.class::cast)
            .findAny();
        assertThat(consoleAppender).isNotEmpty();
        assertThat(consoleAppender.get().getLayout()).isInstanceOf(PatternLayout.class);

        Optional<RollingFileAppender> fileAppender = configuration.getAppenders().values().stream()
            .filter(RollingFileAppender.class::isInstance)
            .map(RollingFileAppender.class::cast)
            .findAny();
        assertThat(fileAppender).isNotEmpty();
        assertThat(fileAppender.get().getLayout()).isInstanceOf(EcsLayout.class);
    }

    @Test
    void testSoutJsonTempJson() {
        Configuration configuration = getLogConfig(Map.of("log_format_sout", "json"));

        assertThat(configuration.getAppenders().values()).hasSize(2);
        Optional<ConsoleAppender> consoleAppender = configuration.getAppenders().values().stream()
            .filter(ConsoleAppender.class::isInstance)
            .map(ConsoleAppender.class::cast)
            .findAny();
        assertThat(consoleAppender).isNotEmpty();
        assertThat(consoleAppender.get().getLayout()).isInstanceOf(EcsLayout.class);

        Optional<RollingFileAppender> fileAppender = configuration.getAppenders().values().stream()
            .filter(RollingFileAppender.class::isInstance)
            .map(RollingFileAppender.class::cast)
            .findAny();
        assertThat(fileAppender).isNotEmpty();
        assertThat(fileAppender.get().getLayout()).isInstanceOf(EcsLayout.class);
    }

    @Test
    void testLevelOff() {
        Configuration configuration = getLogConfig(Map.of("log_level", "off"));
        assertThat(configuration.getRootLogger().getLevel()).isEqualTo(Level.OFF);
    }

    private Configuration getLogConfig(Map<String, String> config) {
        return new Log4j2ConfigurationFactory(List.of(new AbstractConfigurationSource() {
            @Override
            public String getValue(String key) {
                return config.get(key);
            }

            @Override
            public String getName() {
                return config.toString();
            }
        }), "ephemeralId").getConfiguration();
    }
}
