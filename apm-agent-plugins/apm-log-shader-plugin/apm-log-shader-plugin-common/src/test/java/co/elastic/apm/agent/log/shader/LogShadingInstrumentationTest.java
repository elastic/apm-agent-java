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
import co.elastic.apm.agent.logging.LogEcsReformatting;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
    private final ObjectMapper objectMapper;
    private LoggingConfiguration loggingConfig;

    public LogShadingInstrumentationTest() {
        logger = createLoggerFacade();
        objectMapper = new ObjectMapper();
    }

    @BeforeEach
    public void setup() {
        logger.open();
        loggingConfig = config.getConfig(LoggingConfiguration.class);
        when(loggingConfig.getLogEcsReformatting()).thenReturn(LogEcsReformatting.SHADE);
    }

    private void initiateShadeDir(String dirName) throws IOException {
        when(loggingConfig.getLogEcsFormattingDestinationDir()).thenReturn(dirName);
        Files.deleteIfExists(Paths.get(getShadeLogFilePath()));
        Files.deleteIfExists(Paths.get(getShadeLogFilePath() + ".1"));
    }

    @AfterEach
    public void closeLogger() {
        logger.close();
    }

    protected abstract LoggerFacade createLoggerFacade();

    @Test
    public void testSimpleLogShading() throws Exception {
        initiateShadeDir("simple");
        runSimpleScenario();
    }

    private void runSimpleScenario() throws Exception {
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
    public void testShadingIntoOriginalLogsDir() throws Exception {
        initiateShadeDir("");
        runSimpleScenario();
    }

    @Test
    public void testLogShadingDisabled() throws Exception {
        initiateShadeDir("disabled");
        logger.trace(TRACE_MESSAGE);
        when(loggingConfig.getLogEcsReformatting()).thenReturn(LogEcsReformatting.OFF);
        logger.debug(DEBUG_MESSAGE);
        logger.warn(WARN_MESSAGE);
        when(loggingConfig.getLogEcsReformatting()).thenReturn(LogEcsReformatting.SHADE);
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
        initiateShadeDir("replace");
        when(loggingConfig.getLogEcsReformatting()).thenReturn(LogEcsReformatting.REPLACE);
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
        return readShadeLogFile(getShadeLogFilePath());
    }

    @Nonnull
    private ArrayList<JsonNode> readShadeLogFile(String shadeLogFilePath) throws IOException {
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
    protected String getShadeLogFilePath() {
        return Utils.computeShadeLogFilePath(logger.getLogFilePath(), loggingConfig.getLogEcsFormattingDestinationDir());
    }

    private void verifyEcsFormat(String[] splitRawLogLine, JsonNode ecsLogLineTree, @Nullable String traceId) throws Exception {
        SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        Date rawTimestamp = timestampFormat.parse(splitRawLogLine[0]);
        timestampFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date ecsTimestamp = timestampFormat.parse(ecsLogLineTree.get("@timestamp").textValue());
        assertThat(rawTimestamp).isEqualTo(ecsTimestamp);
        assertThat(splitRawLogLine[1]).isEqualTo(ecsLogLineTree.get("process.thread.name").textValue());
        assertThat(splitRawLogLine[2]).isEqualTo(ecsLogLineTree.get("log.level").textValue());
        assertThat(splitRawLogLine[3]).isEqualTo(ecsLogLineTree.get("log.logger").textValue());
        assertThat(splitRawLogLine[4]).isEqualTo(ecsLogLineTree.get("message").textValue());
        String serviceName = tracer.getMetaData().get(2000, TimeUnit.MILLISECONDS).getService().getName();
        assertThat(ecsLogLineTree.get("service.name").textValue()).isEqualTo(serviceName);
        if (traceId != null) {
            assertThat(ecsLogLineTree.get("trace.id").textValue()).isEqualTo(traceId);
        } else {
            assertThat(ecsLogLineTree.get("trace.id")).isNull();
        }
    }

    /**
     * Tests our log file rolling configurations to verify it works as expected. Currently we allow one backup file
     * (meaning - two log files at most) and the decision to roll is based on the {@code log_file_size} configuration.
     * Because of the way Logback and log4j2 make their rolling decision, this test uses a fixed-duration sleep, which
     * is a notorious way to make tests flaky. If that proves to be the case, this test can be disabled, as its
     * importance for regression testing is not crucial. It would be very useful if we decide to modify anything in
     * our logging configuration, for example - change the rolling decision strategy.
     * @throws IOException thrown if we fail to read the shade log file
     */
    @Test
    public void testShadeLogRolling() throws IOException {
        initiateShadeDir("rolling");
        when(loggingConfig.getLogFileSize()).thenReturn(100L);
        logger.trace("First line");
        waitForFileRolling();
        logger.debug("Second Line");
        waitForFileRolling();
        logger.trace("Third line");
        waitForFileRolling();
        logger.debug("Fourth line");
        waitForFileRolling();

        // All tests are configured so that only one line can fit a log file before being rolled.
        // However, while in log4j2 and Logback file rolling takes place BEFORE appending the new log event, in
        // log4j1 this happens AFTER the event is logged. This means we can only count on the non-active file to
        // contain a single line
        String shadeLogFilePath = getShadeLogFilePath();
        ArrayList<JsonNode> jsonNodes = readShadeLogFile(shadeLogFilePath + ".1");
        assertThat(jsonNodes).hasSize(1);
    }

    protected abstract void waitForFileRolling();
}
