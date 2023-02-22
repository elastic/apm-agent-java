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
package co.elastic.apm.agent.jul;

import co.elastic.apm.agent.loginstr.LoggerFacade;
import co.elastic.apm.agent.loginstr.LoggingInstrumentationTest;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class JulInstrumentationTest extends LoggingInstrumentationTest {

    @Override
    protected LoggerFacade createLoggerFacade() {
        return new JulLoggerFacade();
    }

    @Override
    protected boolean logsThreadName() {
        return false;
    }

    @Override
    protected void waitForFileRolling() {
        await().untilAsserted(() -> assertThat(new File(getLogReformattingFilePath()).length()).isEqualTo(0));
    }

    @Override
    protected String getLogReformattingFilePath() {
        // in JUL, the base file also gets the generation number 0
        return super.getLogReformattingFilePath() + ".0";
    }

    /**
     * Custom log levels that match other logging frameworks
     */
    private static class CustomLevel extends java.util.logging.Level {

        protected CustomLevel(final String name, final int value) {
            super(name, value);
        }

        public static final CustomLevel FATAL = new CustomLevel("FATAL", 1100);
        public static final CustomLevel ERROR = new CustomLevel("ERROR", 1000);
        public static final CustomLevel WARN = new CustomLevel("WARN", 900);
        public static final CustomLevel INFO = new CustomLevel("INFO", 800);
        public static final CustomLevel DEBUG = new CustomLevel("DEBUG", 500);
        public static final CustomLevel TRACE = new CustomLevel("TRACE", 400);
    }

    private static class JulLoggerFacade extends AbstractJulLoggerFacade {

        @Override
        protected void resetRemovedHandler(){
            if (Arrays.stream(julLogger.getHandlers()).noneMatch(handler -> handler instanceof FileHandler)) {
                try {
                    julLogger.addHandler(new FileHandler());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public String getLogFilePath() {
            for (Handler loggerHandler : julLogger.getHandlers()) {
                if (loggerHandler instanceof FileHandler) {
                    // no API for that, so we use reflection for tests and the field in the instrumentation
                    try {
                        File[] files = getField(loggerHandler,"files");
                        return files[0].getAbsolutePath();
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to get log file path through reflection", e);
                    }
                }
            }
            throw new IllegalStateException("Couldn't find a FileHandler for logger " + julLogger.getName());
        }
    }

    protected abstract static class AbstractJulLoggerFacade implements LoggerFacade {

        static {
            try {
                LogManager.getLogManager().readConfiguration(JulLoggerFacade.class.getClassLoader().getResourceAsStream("logging.properties"));
            } catch (IOException e) {
                throw new RuntimeException("Failed to read the JUL test configuration file");
            }
        }

        protected Logger julLogger;

        protected abstract void resetRemovedHandler();

        @Override
        public void open() {
            julLogger = Logger.getLogger("Test-File-Logger");

            // In case there is no file handler as it was removed through close().
            // The reason not to re-add through close() is that this deletes the file and sometimes we want to manually review the
            // original log file after the test ends.
            resetRemovedHandler();
        }

        @Override
        public void close() {
            // Closing and replacing the log file handler so that the log file would be rewritten
            for (Handler handler : julLogger.getHandlers()) {
                handler.close();
                julLogger.removeHandler(handler);
            }
        }

        @Override
        public void trace(String message) {
            julLogger.log(CustomLevel.TRACE, message);
        }

        @Override
        public void debug(String message) {
            julLogger.log(CustomLevel.DEBUG, message);
        }

        @Override
        public void warn(String message) {
            julLogger.log(CustomLevel.WARN, message);
        }

        @Override
        public void error(String message) {
            julLogger.log(CustomLevel.ERROR, message);
        }

        @Override
        public void error(String message, Throwable throwable) {
            julLogger.log(CustomLevel.ERROR, message, throwable);
        }

        @Override
        public void putTraceIdToMdc(String traceId) {
            // not supported for JUL
        }

        @Override
        public void removeTraceIdFromMdc() {
            // not supported for JUL
        }

        protected <T extends Handler,X> X getField(T handler, String fieldName) throws IllegalAccessException, NoSuchFieldException {
            Field field = handler.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (X)field.get(handler);
        }
    }
}
