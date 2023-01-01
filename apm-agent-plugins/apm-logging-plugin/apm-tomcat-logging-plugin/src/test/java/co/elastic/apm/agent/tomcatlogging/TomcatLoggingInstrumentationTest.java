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
package co.elastic.apm.agent.tomcatlogging;

import co.elastic.apm.agent.jul.JulInstrumentationTest;
import co.elastic.apm.agent.loginstr.LoggerFacade;
import org.apache.juli.FileHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Handler;
import java.util.logging.SimpleFormatter;

public class TomcatLoggingInstrumentationTest extends JulInstrumentationTest {

    @Override
    protected LoggerFacade createLoggerFacade() {
        // we need to delete previous log files before the logger instance is created
        // as a consequence, we can't rely on Junit @BeforeEach ordering with parent test class
        cleanupPreviousLogFiles();
        return new TomcatLoggerFacade();
    }

    private static void cleanupPreviousLogFiles() {
        Path folder = Paths.get("target", "tomcat");
        String prefix = String.format("catalina.%s", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        try {
            Files.deleteIfExists(folder.resolve(prefix + ".log"));
            Files.deleteIfExists(folder.resolve(prefix + ".ecs.json.0"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class TomcatLoggerFacade extends JulInstrumentationTest.AbstractJulLoggerFacade {

        @Override
        protected void resetRemovedHandler() {
            if (Arrays.stream(julLogger.getHandlers()).noneMatch(handler -> handler instanceof FileHandler)) {
                try {
                    FileHandler fileHandler = new FileHandler();
                    fileHandler.setFormatter(new SimpleFormatter());
                    julLogger.addHandler(fileHandler);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void close() {
            super.close();
            cleanupPreviousLogFiles();
        }

        @Override
        public String getLogFilePath() {
            for (Handler loggerHandler : julLogger.getHandlers()) {
                if (loggerHandler instanceof FileHandler) {
                    // no API for that, so we use reflection for tests and the field in the instrumentation

                    try {
                        String directory = getField(loggerHandler, "directory");
                        String prefix = getField(loggerHandler, "prefix");
                        String suffix = getField(loggerHandler, "suffix");
                        String date = getField(loggerHandler, "date");

                        return Paths.get(directory).toAbsolutePath()
                            .resolve(prefix + date + suffix)
                            .toString();
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to get log file path through reflection", e);
                    }
                }
            }
            throw new IllegalStateException("Couldn't find a FileHandler for logger " + julLogger.getName());
        }
    }
}
