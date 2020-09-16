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

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public abstract class LogShadingInstrumentationTest extends AbstractInstrumentationTest {

    public static final String TRACE_MESSAGE = "Trace-this";
    public static final String DEBUG_MESSAGE = "Debug-this";
    public static final String WARN_MESSAGE = "Warn-this";
    public static final String ERROR_MESSAGE = "Error-this";

    private final LoggerFacade logger;
    private ObjectMapper objectMapper;

    public LogShadingInstrumentationTest() {
        logger = getLoggerFacade();
        objectMapper = new ObjectMapper();
    }

    @Before
    public void setup() throws IOException {
        logger.open();
        Files.deleteIfExists(Paths.get(getShadeLogFilePath()));
    }

    @After
    public void closeLogger() {
        logger.close();
    }

    protected abstract LoggerFacade getLoggerFacade();

    @Test
    public void testSimpleLogShading() throws IOException, ParseException {
        String traceId = UUID.randomUUID().toString();
        logger.putTraceIdToMdc(traceId);
        try {
            logger.trace(TRACE_MESSAGE);
            logger.debug(DEBUG_MESSAGE);
            logger.warn(WARN_MESSAGE);
            logger.error(ERROR_MESSAGE);
        } finally {
            logger.removeTraceIdFromMdc();
        }

        ArrayList<String[]> rawLogLines = readRawLogLines();
        assertThat(rawLogLines).hasSize(4);

        ArrayList<JsonNode> ecsLogLines = readShadeLogFile();
        assertThat(ecsLogLines).hasSize(4);

        for (int i = 0; i < 4; i++) {
            verifyEcsFormat(rawLogLines.get(i), ecsLogLines.get(i), traceId);
        }
    }

    @Test
    public void testShadingIntoConfiguredDir() throws IOException, ParseException {
        when(config.getConfig(LoggingConfiguration.class).getLogShadingDestinationDir()).thenReturn("shade_logs");
        Files.deleteIfExists(Paths.get(getShadeLogFilePath()));
        testSimpleLogShading();
    }

    @Test
    public void testLogShadingDisabled() throws IOException, ParseException {
        logger.trace(TRACE_MESSAGE);
        when(config.getConfig(LoggingConfiguration.class).isLogShadingEnabled()).thenReturn(false);
        logger.debug(DEBUG_MESSAGE);
        logger.warn(WARN_MESSAGE);
        when(config.getConfig(LoggingConfiguration.class).isLogShadingEnabled()).thenReturn(true);
        logger.error(ERROR_MESSAGE);

        ArrayList<String[]> rawLogLines = readRawLogLines();
        assertThat(rawLogLines).hasSize(4);

        ArrayList<JsonNode> ecsLogLines = readShadeLogFile();
        assertThat(ecsLogLines).hasSize(2);
        verifyEcsFormat(rawLogLines.get(0), ecsLogLines.get(0), null);
        verifyEcsFormat(rawLogLines.get(3), ecsLogLines.get(1), null);
    }

    @Test
    public void testLogShadingReplaceOriginal() throws IOException {
        when(config.getConfig(LoggingConfiguration.class).isLogShadingReplaceEnabled()).thenReturn(true);
        logger.trace(TRACE_MESSAGE);
        logger.debug(DEBUG_MESSAGE);
        logger.warn(WARN_MESSAGE);
        logger.error(ERROR_MESSAGE);

        assertThat(readRawLogLines()).isEmpty();
        ArrayList<JsonNode> shadeLogEvents = readShadeLogFile();
        assertThat(shadeLogEvents).hasSize(4);
        for (JsonNode ecsLogLineTree : shadeLogEvents) {
            assertThat(ecsLogLineTree.get("process.thread.name")).isNotNull();
            assertThat(ecsLogLineTree.get("log.level")).isNotNull();
            assertThat(ecsLogLineTree.get("log.logger")).isNotNull();
            assertThat(ecsLogLineTree.get("message")).isNotNull();
        }
    }

    @Nonnull
    private ArrayList<JsonNode> readShadeLogFile() throws IOException {
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
        return ecsLogLines;
    }

    @Nonnull
    private ArrayList<String[]> readRawLogLines() throws IOException {
        ArrayList<String[]> rawLogLines;
        try (Stream<String> stream = Files.lines(getOriginalLogFilePath())) {
            rawLogLines = stream.map(line -> line.split("\\s+")).collect(Collectors.toCollection(ArrayList::new));
        }
        return rawLogLines;
    }

    @Nonnull
    private Path getOriginalLogFilePath() {
        return Paths.get(logger.getLogFilePath());
    }

    @Nonnull
    private String getShadeLogFilePath() {
        return Utils.computeShadeLogFilePath(logger.getLogFilePath());
    }

    private void verifyEcsFormat(String[] splitRawLogLine, JsonNode ecsLogLineTree, @Nullable String traceId) throws ParseException {
        SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        Date rawTimestamp = timestampFormat.parse(splitRawLogLine[0]);
        timestampFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date ecsTimestamp = timestampFormat.parse(ecsLogLineTree.get("@timestamp").textValue());
        assertThat(rawTimestamp).isEqualTo(ecsTimestamp);
        assertThat(splitRawLogLine[1]).isEqualTo(ecsLogLineTree.get("process.thread.name").textValue());
        assertThat(splitRawLogLine[2]).isEqualTo(ecsLogLineTree.get("log.level").textValue());
        assertThat(splitRawLogLine[3]).isEqualTo(ecsLogLineTree.get("log.logger").textValue());
        assertThat(splitRawLogLine[4]).isEqualTo(ecsLogLineTree.get("message").textValue());
        assertThat(ecsLogLineTree.get("service.name").textValue()).isEqualTo(tracer.getMetaData().getService().getName());
        if (traceId != null) {
            assertThat(ecsLogLineTree.get("trace.id").textValue()).isEqualTo(traceId);
        } else {
            assertThat(ecsLogLineTree.get("trace.id")).isNull();
        }
    }

    /**
     * Disabled by default, as this is a very slow test. Can be used for manual testing of shade file rolling.
     * Note: logback and log4j2 rollover before appending an event, which means the two log files will contain messages.
     * As opposed to those, log4j1 rolls over after appending an event, which means that the active log file (log4j1.log)
     * will be empty when the test ends.
     */
    // @Test
    public void testShadeLogRolling() {
        when(config.getConfig(LoggingConfiguration.class).getLogFileSize()).thenReturn(100L);
        logger.trace("First line");
        sleep();
        logger.debug("Second Line");
        sleep();
        logger.trace("Third line");
        sleep();
        logger.debug("Fourth line");
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
