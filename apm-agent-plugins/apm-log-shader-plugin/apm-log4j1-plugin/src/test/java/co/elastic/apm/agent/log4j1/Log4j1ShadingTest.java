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
package co.elastic.apm.agent.log4j1;

import co.elastic.apm.agent.log.shader.LogShadingInstrumentationTest;
import co.elastic.apm.agent.log.shader.LoggerFacade;
import org.apache.log4j.FileAppender;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.WriterAppender;
import org.junit.jupiter.api.Disabled;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class Log4j1ShadingTest extends LogShadingInstrumentationTest {

    @Override
    protected LoggerFacade createLoggerFacade() {
        return new Log4j1LoggerFacade();
    }

    @Override
    protected void waitForFileRolling() {
        await().untilAsserted(() -> assertThat(new File(getShadeLogFilePath()).length()).isEqualTo(0));
    }

    private static class Log4j1LoggerFacade implements LoggerFacade {

        private Logger log4j1Logger;

        public Log4j1LoggerFacade() {
            String log4j1ConfigFile = Objects.requireNonNull(Log4j1LoggerFacade.class.getClassLoader()
                .getResource("log4j1.properties")).getFile();
            PropertyConfigurator.configure(log4j1ConfigFile);
        }

        @Override
        public void open() {
            log4j1Logger = LogManager.getLogger("Test-File-Logger");
        }

        @Override
        public void close() {
            Log4j1LogShadingHelper.instance().closeShadeAppender((WriterAppender) log4j1Logger.getAppender("FILE"));
            LogManager.shutdown();
        }

        @Override
        public String getLogFilePath() {
            return ((FileAppender) log4j1Logger.getAppender("FILE")).getFile();
        }

        @Override
        public void trace(String message) {
            log4j1Logger.trace(message);
        }

        @Override
        public void debug(String message) {
            log4j1Logger.debug(message);
        }

        @Override
        public void warn(String message) {
            log4j1Logger.warn(message);
        }

        @Override
        public void error(String message) {
            log4j1Logger.error(message);
        }

        @Override
        public void error(String message, Throwable throwable) {
            log4j1Logger.error(message, throwable);
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
