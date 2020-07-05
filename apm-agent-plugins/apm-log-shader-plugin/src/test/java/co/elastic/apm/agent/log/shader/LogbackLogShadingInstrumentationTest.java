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
package co.elastic.apm.agent.log.shader;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.FileAppender;
import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class LogbackLogShadingInstrumentationTest extends AbstractInstrumentationTest {

    public static final String TRACE_MESSAGE = "Trace-this";
    public static final String DEBUG_MESSAGE = "Debug-this";
    public static final String WARN_MESSAGE = "Warn-this";
    public static final String ERROR_MESSAGE = "Error-this";
    public static final String SERVICE_NAME = "LogbackTest";

    private Logger logbackLogger;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() throws IOException {
        when(config.getConfig(CoreConfiguration.class).getServiceName()).thenReturn(SERVICE_NAME);
        logbackLogger = (Logger) LoggerFactory.getLogger("Test-File-Logger");
        objectMapper = new ObjectMapper();
        Files.deleteIfExists(Paths.get(getShadeLogFilePath()));
    }

    @Test
    void testSimpleLogShading() throws IOException, ParseException {
        String traceId = "afiuawrwuehrwu";
        MDC.put("trace.id", traceId);
        logbackLogger.trace(TRACE_MESSAGE);
        logbackLogger.debug(DEBUG_MESSAGE);
        logbackLogger.warn(WARN_MESSAGE);
        logbackLogger.error(ERROR_MESSAGE);

        ArrayList<String[]> rawLogLines;
        try (Stream<String> stream = Files.lines(Paths.get(getLogFilePath()))) {
            rawLogLines = stream.map(line -> line.split("\\s+")).collect(Collectors.toCollection(ArrayList::new));
        }
        assertThat(rawLogLines).hasSize(4);

        String shadeLogFilePath = getShadeLogFilePath();
        ArrayList<JsonNode> ecsLogLines = new ArrayList<>();
        try (Stream<String> stream = Files.lines(Paths.get(shadeLogFilePath))) {
            stream.forEach(line -> {
                try {
                    ecsLogLines.add(objectMapper.readTree(line));
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            });
        }
        assertThat(ecsLogLines).hasSize(4);

        for (int i = 0; i < 4; i++) {
            verifyEcsFormat(rawLogLines.get(i), ecsLogLines.get(i), traceId);
        }
    }

    @Nonnull
    private String getShadeLogFilePath() {
        return Utils.computeShadeLogFilePath(getLogFilePath());
    }

    private String getLogFilePath() {
        return ((FileAppender<?>) logbackLogger.getAppender("FILE")).getFile();
    }

    private void verifyEcsFormat(String[] splitRawLogLine, JsonNode ecsLogLineTree, String traceId) throws ParseException {
        SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        Date rawTimestamp = timestampFormat.parse(splitRawLogLine[0]);
        timestampFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date ecsTimestamp = timestampFormat.parse(ecsLogLineTree.get("@timestamp").textValue());
        assertThat(rawTimestamp).isEqualTo(ecsTimestamp);
        assertThat(splitRawLogLine[1]).isEqualTo(ecsLogLineTree.get("process.thread.name").textValue());
        assertThat(splitRawLogLine[2]).isEqualTo(ecsLogLineTree.get("log.level").textValue());
        assertThat(splitRawLogLine[3]).isEqualTo(ecsLogLineTree.get("log.logger").textValue());
        assertThat(splitRawLogLine[4]).isEqualTo(ecsLogLineTree.get("message").textValue());
        assertThat(ecsLogLineTree.get("service.name").textValue()).isEqualTo(SERVICE_NAME);
        assertThat(ecsLogLineTree.get("trace.id").textValue()).isEqualTo(traceId);
    }

    // Disabled - very slow. Can be used for file rolling manual testing
    // @Test
    void testShadeLogRolling() {
        when(config.getConfig(LoggingConfiguration.class).getLogFileSize()).thenReturn(100L);
        logbackLogger.trace("First line");
        sleep();
        logbackLogger.debug("Second Line");
        sleep();
        logbackLogger.trace("Third line");
        sleep();
        logbackLogger.debug("Fourth line");
        sleep();
    }

    private void sleep() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
