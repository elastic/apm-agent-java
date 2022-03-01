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
package co.elastic.apm.agent.logback;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.joran.spi.JoranException;
import co.elastic.apm.agent.logging.LoggingInstrumentationTest;
import co.elastic.apm.agent.logging.LoggerFacade;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.net.URL;

public class LogbackInstrumentationTest extends LoggingInstrumentationTest {

    private static final Marker TEST_MARKER = MarkerFactory.getMarker("TEST");

    @Override
    protected LoggerFacade createLoggerFacade() {
        return new LogbackLoggerFacade();
    }

    @Override
    protected boolean markersSupported() {
        return true;
    }

    @Override
    protected void waitForFileRolling() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static class LogbackLoggerFacade implements LoggerFacade {

        private Logger logbackLogger;

        @Override
        public void open() {
            LoggerContext loggerContext = new LoggerContext();
            ContextInitializer contextInitializer = new ContextInitializer(loggerContext);
            try {
                // Get a configuration file from classpath
                URL configurationUrl = Thread.currentThread().getContextClassLoader().getResource("logback.xml");
                if (configurationUrl == null) {
                    throw new IllegalStateException("Unable to find custom logback configuration file");
                }
                // Ask context initializer to load configuration into context
                contextInitializer.configureByResource(configurationUrl);
            } catch (JoranException e) {
                throw new RuntimeException("Unable to configure logger", e);
            }
            logbackLogger = loggerContext.getLogger("Test-File-Logger");
        }

        @Override
        public void close() {
            logbackLogger.detachAndStopAllAppenders();
        }

        @Override
        public String getLogFilePath() {
            return ((FileAppender<?>) logbackLogger.getAppender("FILE")).getFile();
        }

        @Override
        public void trace(String message) {
            logbackLogger.trace(message);
        }

        @Override
        public void debug(String message) {
            logbackLogger.debug(message);
        }

        @Override
        public void debugWithMarker(String message) {
            logbackLogger.debug(TEST_MARKER, message);
        }

        @Override
        public void warn(String message) {
            logbackLogger.warn(message);
        }

        @Override
        public void error(String message) {
            logbackLogger.error(message);
        }

        @Override
        public void error(String message, Throwable throwable) {
            logbackLogger.error(message, throwable);
        }

        @Override
        public void putTraceIdToMdc(String traceId) {
            MDC.put("trace.id", traceId);
        }

        @Override
        public void removeTraceIdFromMdc() {
            MDC.remove("trace.id");
        }
    }
}
