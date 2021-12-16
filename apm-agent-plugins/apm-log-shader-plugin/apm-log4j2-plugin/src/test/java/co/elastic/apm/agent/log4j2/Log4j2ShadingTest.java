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
package co.elastic.apm.agent.log4j2;

import co.elastic.apm.agent.log.shader.LogShadingInstrumentationTest;
import co.elastic.apm.agent.log.shader.LoggerFacade;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.appender.RandomAccessFileAppender;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

public class Log4j2ShadingTest extends LogShadingInstrumentationTest {

    @BeforeAll
    static void resetConfigFactory() {
        ConfigurationFactory.resetConfigurationFactory();
    }

    @AfterAll
    static void reInitLogging() {
        LoggingConfiguration.init(List.of(), "");
    }

    private static final Marker TEST_MARKER = MarkerManager.getMarker("TEST");

    @Override
    protected LoggerFacade createLoggerFacade() {
        return new Log4j2LoggerFacade();
    }

    @Override
    protected boolean markersSupported() {
        return true;
    }

    @Override
    protected void waitForFileRolling() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static class Log4j2LoggerFacade implements LoggerFacade {

        private Logger log4j2Logger;
        private URI configLocation;

        public Log4j2LoggerFacade() {
            try {
                configLocation = Objects.requireNonNull(Log4j2ShadingTest.class.getClassLoader()
                    .getResource("log4j2.xml")).toURI();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void open() {
            log4j2Logger = LogManager.getLogger("Test-File-Logger");
            ((org.apache.logging.log4j.core.Logger) log4j2Logger).getContext().setConfigLocation(configLocation);
        }

        @Override
        public void close() {
            LogManager.shutdown();
        }

        @Override
        public String getLogFilePath() {
            return ((RandomAccessFileAppender) ((org.apache.logging.log4j.core.Logger) log4j2Logger).getAppenders().get("FILE")).getFileName();
        }

        @Override
        public void trace(String message) {
            log4j2Logger.trace(message);
        }

        @Override
        public void debug(String message) {
            log4j2Logger.debug(message);
        }

        @Override
        public void debugWithMarker(String message) {
            log4j2Logger.debug(TEST_MARKER, message);
        }

        @Override
        public void warn(String message) {
            log4j2Logger.warn(message);
        }

        @Override
        public void error(String message) {
            log4j2Logger.error(message);
        }

        @Override
        public void error(String message, Throwable throwable) {
            log4j2Logger.error(message, throwable);
        }

        @Override
        public void putTraceIdToMdc(String traceId) {
            ThreadContext.put("trace.id", traceId);
        }

        @Override
        public void removeTraceIdFromMdc() {
            ThreadContext.remove("trace.id");
        }
    }
}
