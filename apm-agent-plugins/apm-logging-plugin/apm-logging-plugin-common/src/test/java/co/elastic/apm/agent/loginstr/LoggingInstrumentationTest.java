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
package co.elastic.apm.agent.loginstr;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.logging.LogEcsReformatting;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import co.elastic.apm.agent.logging.TestUtils;
import co.elastic.apm.agent.loginstr.correlation.AbstractLogCorrelationHelper;
import co.elastic.apm.agent.loginstr.reformatting.Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public abstract class LoggingInstrumentationTest extends AbstractInstrumentationTest {

    public static final String TRACE_MESSAGE = "Trace-this";
    public static final String DEBUG_MESSAGE = "Debug-this";
    public static final String WARN_MESSAGE = "Warn-this";
    public static final String ERROR_MESSAGE = "Error-this";

    private static final String SERVICE_VERSION = "v42";

    private static final String SERVICE_NODE_NAME = "my-service-node";
    private static final Map<String, String> ADDITIONAL_FIELDS = Map.of("some.field", "some-value", "another.field", "another-value");
    private static final String ENVIRONMENT = "my-env";

    private final LoggerFacade logger;
    private final ObjectMapper objectMapper;
    private final SimpleDateFormat timestampFormat;
    private final SimpleDateFormat utcTimestampFormat;

    private LoggingConfiguration loggingConfig;
    private String serviceName;
    private Transaction transaction;
    private Span childSpan;

    public LoggingInstrumentationTest() {
        logger = createLoggerFacade();
        objectMapper = new ObjectMapper();
        timestampFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        utcTimestampFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        utcTimestampFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Before
    @BeforeEach
    public void setup() throws Exception {
        doReturn(SERVICE_VERSION).when(config.getConfig(CoreConfiguration.class)).getServiceVersion();
        doReturn(ENVIRONMENT).when(config.getConfig(CoreConfiguration.class)).getEnvironment();
        doReturn(SERVICE_NODE_NAME).when(config.getConfig(CoreConfiguration.class)).getServiceNodeName();

        loggingConfig = config.getConfig(LoggingConfiguration.class);
        doReturn(ADDITIONAL_FIELDS).when(loggingConfig).getLogEcsReformattingAdditionalFields();

        logger.open();

        // IMPORTANT: keep this last, so that it doesn't interfere with Mockito settings above
        serviceName = Objects.requireNonNull(tracer.getMetaDataFuture().get(2000, TimeUnit.MILLISECONDS).getService().getName());

        transaction = startTestRootTransaction();
        childSpan = transaction.createSpan().activate();
    }

    private void setEcsReformattingConfig(LogEcsReformatting ecsReformattingConfig) {
        doReturn(ecsReformattingConfig).when(loggingConfig).getLogEcsReformatting();
    }

    private void initializeReformattingDir(String dirName) throws IOException {
        doReturn(dirName).when(loggingConfig).getLogEcsFormattingDestinationDir();
        Files.deleteIfExists(Paths.get(getLogReformattingFilePath()));
        Files.deleteIfExists(Paths.get(getLogReformattingFilePath() + ".1"));
    }

    @After
    @AfterEach
    public void closeLogger() {
        childSpan.deactivate().end();
        transaction.deactivate().end();
        logger.close();
    }

    protected abstract LoggerFacade createLoggerFacade();

    protected boolean logsThreadName() {
        return true;
    }

    @Test
    public void testSimpleLogReformatting() throws Exception {
        setEcsReformattingConfig(LogEcsReformatting.SHADE);
        initializeReformattingDir("simple");
        runSimpleScenario();
    }

    private void runSimpleScenario() throws Exception {
        logger.trace(TRACE_MESSAGE);
        logger.debug(DEBUG_MESSAGE);
        logger.warn(WARN_MESSAGE);
        logger.error(ERROR_MESSAGE, new Throwable());

        ArrayList<String[]> rawLogLines = readRawLogLines();
        assertThat(rawLogLines).hasSize(4);

        ArrayList<JsonNode> ecsLogLines = readEcsLogFile();
        assertThat(ecsLogLines).hasSize(4);

        for (int i = 0; i < 4; i++) {
            verifyEcsFormat(rawLogLines.get(i), ecsLogLines.get(i));
        }
    }

    @Test
    public void testMarkers() throws Exception {
        if (markersSupported()) {
            setEcsReformattingConfig(LogEcsReformatting.SHADE);
            initializeReformattingDir("markers");
            logger.debugWithMarker(DEBUG_MESSAGE);

            ArrayList<String[]> rawLogLines = readRawLogLines();
            assertThat(rawLogLines).hasSize(1);
            String[] rawLogLine = rawLogLines.get(0);

            ArrayList<JsonNode> ecsLogLines = readEcsLogFile();
            assertThat(ecsLogLines).hasSize(1);
            JsonNode ecsLogLine = ecsLogLines.get(0);

            verifyEcsFormat(rawLogLine, ecsLogLine);

            JsonNode tagsJson = ecsLogLine.get("tags");
            assertThat(tagsJson.isArray()).isTrue();
            assertThat(tagsJson.get(0).textValue()).isEqualTo("TEST");
        }
    }

    protected boolean markersSupported() {
        return false;
    }

    @Test
    public void testShadingIntoOriginalLogsDir() throws Exception {
        setEcsReformattingConfig(LogEcsReformatting.SHADE);
        initializeReformattingDir("");
        runSimpleScenario();
    }

    @Test
    public void testLazyEcsFileCreation() throws Exception {
        initializeReformattingDir("delayed");
        logger.trace(TRACE_MESSAGE);
        logger.debug(DEBUG_MESSAGE);
        assertThat(Paths.get(getLogReformattingFilePath())).doesNotExist();
        setEcsReformattingConfig(LogEcsReformatting.SHADE);
        logger.warn(WARN_MESSAGE);
        assertThat(Paths.get(getLogReformattingFilePath())).exists();
        logger.error(ERROR_MESSAGE, new Throwable());

        ArrayList<String[]> rawLogLines = readRawLogLines();
        assertThat(rawLogLines).hasSize(4);

        ArrayList<JsonNode> ecsLogLines = readEcsLogFile();
        assertThat(ecsLogLines).hasSize(2);
        verifyEcsFormat(rawLogLines.get(2), ecsLogLines.get(0));
        verifyEcsFormat(rawLogLines.get(3), ecsLogLines.get(1));
    }

    @Test
    public void testLogReformattingReplaceOriginal() throws IOException {
        initializeReformattingDir("replace");
        setEcsReformattingConfig(LogEcsReformatting.REPLACE);
        logger.trace(TRACE_MESSAGE);
        logger.debug(DEBUG_MESSAGE);
        logger.warn(WARN_MESSAGE);
        logger.error(ERROR_MESSAGE, new Throwable());

        assertThat(readRawLogLines()).isEmpty();
        ArrayList<JsonNode> ecsLogEvents = readEcsLogFile();
        assertThat(ecsLogEvents).hasSize(4);
        for (JsonNode ecsLogLineTree : ecsLogEvents) {
            verifyEcsLogLine(ecsLogLineTree);
        }
    }

    @Test
    public void testLogOverride() throws IOException {
        setEcsReformattingConfig(LogEcsReformatting.OVERRIDE);
        logger.trace(TRACE_MESSAGE);
        logger.debug(DEBUG_MESSAGE);
        logger.warn(WARN_MESSAGE);
        logger.error(ERROR_MESSAGE, new Throwable());

        ArrayList<JsonNode> overriddenLogEvents = TestUtils.readJsonFile(getOriginalLogFilePath().toString());
        for (JsonNode ecsLogLineTree : overriddenLogEvents) {
            verifyEcsLogLine(ecsLogLineTree);
        }
    }

    @Test
    public void testSendLogs() {
        doReturn(Boolean.TRUE).when(loggingConfig).getSendLogs();

        logger.trace(TRACE_MESSAGE);
        logger.debug(DEBUG_MESSAGE);
        logger.warn(WARN_MESSAGE);
        logger.error(ERROR_MESSAGE, new Throwable());

        List<JsonNode> logs = reporter.getLogs()
            .stream()
            // running test with surefire and within IDE do not produce the same 'event.dataset'
            .filter(log -> log.get("event.dataset").textValue().endsWith(".FILE"))
            .collect(Collectors.toList());
        assertThat(logs).hasSize(4);
        for (JsonNode ecsLogLineTree : logs) {
            verifyEcsLogLine(ecsLogLineTree);
        }
    }

    @Test
    public void testEmptyFormatterAllowList() throws Exception {
        initializeReformattingDir("disabled");
        setEcsReformattingConfig(LogEcsReformatting.SHADE);
        doReturn(Collections.EMPTY_LIST).when(loggingConfig).getLogEcsFormatterAllowList();
        logger.trace(TRACE_MESSAGE);
        logger.debug(DEBUG_MESSAGE);
        logger.warn(WARN_MESSAGE);
        logger.error(ERROR_MESSAGE, new Throwable());
        assertThat(readRawLogLines()).hasSize(4);
        assertThat(Paths.get(getLogReformattingFilePath())).doesNotExist();
    }

    @Test
    public void testDynamicConfiguration() throws Exception {
        initializeReformattingDir("dynamic");
        for (int i = 0; i < 2; i++) {
            setEcsReformattingConfig(LogEcsReformatting.OFF);
            logger.trace(TRACE_MESSAGE);
            setEcsReformattingConfig(LogEcsReformatting.OVERRIDE);
            logger.debug(DEBUG_MESSAGE);
            setEcsReformattingConfig(LogEcsReformatting.SHADE);
            logger.warn(WARN_MESSAGE);
            setEcsReformattingConfig(LogEcsReformatting.REPLACE);
            logger.error(ERROR_MESSAGE, new Throwable());
        }

        // ERROR messages should not appear in original log as they were replaced
        ArrayList<String> originalLogLines = Files.lines(getOriginalLogFilePath()).collect(Collectors.toCollection(ArrayList::new));
        assertThat(originalLogLines).hasSize(6);

        // ECS log file should contain only WARN and ERROR messages
        ArrayList<JsonNode> ecsLogLines = readEcsLogFile();
        assertThat(ecsLogLines).hasSize(4);

        // TRACE messages should be only in original file in original format
        assertThat(originalLogLines.get(0)).contains("TRACE ");
        assertThat(originalLogLines.get(3)).contains("TRACE ");

        // DEBUG messages should be ECS-formatted in original file
        JsonNode debugLogLine = objectMapper.readTree(originalLogLines.get(1));
        verifyEcsLogLine(debugLogLine);
        assertThat(debugLogLine.get("log.level").textValue()).isEqualTo("DEBUG");
        debugLogLine = objectMapper.readTree(originalLogLines.get(4));
        verifyEcsLogLine(debugLogLine);
        assertThat(debugLogLine.get("log.level").textValue()).isEqualTo("DEBUG");

        // WARN messages should match content but not format
        verifyEcsFormat(originalLogLines.get(2).split("\\s+"), ecsLogLines.get(0));
        assertThat(ecsLogLines.get(0).get("log.level").textValue()).isEqualTo("WARN");
        verifyEcsFormat(originalLogLines.get(5).split("\\s+"), ecsLogLines.get(2));
        assertThat(ecsLogLines.get(2).get("log.level").textValue()).isEqualTo("WARN");

        // ERROR messages should be only in ECS log file
        verifyEcsLogLine(ecsLogLines.get(1));
        assertThat(ecsLogLines.get(1).get("log.level").textValue()).isEqualTo("ERROR");
        verifyEcsLogLine(ecsLogLines.get(3));
        assertThat(ecsLogLines.get(3).get("log.level").textValue()).isEqualTo("ERROR");
    }

    private void verifyEcsLogLine(JsonNode ecsLogLineTree) {
        String currentThreadName = Thread.currentThread().getName();
        assertThat(ecsLogLineTree.get("@timestamp")).isNotNull();
        assertThat(ecsLogLineTree.get("process.thread.name").textValue()).isEqualTo(currentThreadName);
        JsonNode logLevel = ecsLogLineTree.get("log.level");
        assertThat(logLevel).isNotNull();
        boolean isErrorLine = logLevel.textValue().equalsIgnoreCase("error");
        assertThat(ecsLogLineTree.get("log.logger").textValue()).isEqualTo("Test-File-Logger");
        assertThat(ecsLogLineTree.get("message")).isNotNull();
        verifyTracingMetadata(ecsLogLineTree);
        if (isLogCorrelationSupported()) {
            assertThat(ecsLogLineTree.get(AbstractLogCorrelationHelper.TRACE_ID_MDC_KEY).textValue()).isEqualTo(transaction.getTraceContext().getTraceId().toString());
            assertThat(ecsLogLineTree.get(AbstractLogCorrelationHelper.TRANSACTION_ID_MDC_KEY).textValue()).isEqualTo(transaction.getTraceContext().getTransactionId().toString());
            verifyErrorCaptureAndCorrelation(isErrorLine, ecsLogLineTree);
        } else {
            assertThat(ecsLogLineTree.get(AbstractLogCorrelationHelper.TRACE_ID_MDC_KEY)).isNull();
            assertThat(ecsLogLineTree.get(AbstractLogCorrelationHelper.TRANSACTION_ID_MDC_KEY)).isNull();
        }
    }

    private void verifyTracingMetadata(JsonNode ecsLogLineTree) {
        assertThat(ecsLogLineTree.get("service.name").textValue()).isEqualTo(serviceName);
        assertThat(ecsLogLineTree.get("service.node.name").textValue()).isEqualTo(SERVICE_NODE_NAME);
        assertThat(ecsLogLineTree.get("event.dataset").textValue()).isEqualTo(serviceName + ".FILE");
        assertThat(ecsLogLineTree.get("service.version").textValue()).isEqualTo(SERVICE_VERSION);
        assertThat(ecsLogLineTree.get("service.environment").textValue()).isEqualTo(ENVIRONMENT);
        assertThat(ecsLogLineTree.get("some.field").textValue()).isEqualTo("some-value");
        assertThat(ecsLogLineTree.get("another.field").textValue()).isEqualTo("another-value");
    }

    private void verifyErrorCaptureAndCorrelation(boolean isErrorLine, JsonNode ecsLogLineTree) {
        final JsonNode errorJsonNode = ecsLogLineTree.get(AbstractLogCorrelationHelper.ERROR_ID_MDC_KEY);
        if (isErrorLine) {
            assertThat(errorJsonNode).describedAs("missing error ID").isNotNull();
            List<ErrorCapture> errors = reporter.getErrors().stream()
                .filter(error -> errorJsonNode.textValue().equals(error.getTraceContext().getId().toString()))
                .collect(Collectors.toList());
            assertThat(errors).hasSize(1);
        } else {
            assertThat(errorJsonNode).isNull();
        }
    }

    private ArrayList<JsonNode> readEcsLogFile() throws IOException {
        return TestUtils.readJsonFile(getLogReformattingFilePath());
    }

    private ArrayList<String[]> readRawLogLines() throws IOException {
        ArrayList<String[]> rawLogLines;
        try (Stream<String> stream = Files.lines(getOriginalLogFilePath())) {
            rawLogLines = stream
                .map(line -> line.split("\\s+"))
                // ignoring lines related to Throwable logging
                .filter(lineParts -> lineParts.length > 0)
                .filter(lineParts -> safelyParseDate(lineParts[0]) != null)
                .collect(Collectors.toCollection(ArrayList::new));
        }
        return rawLogLines;
    }

    @Nullable
    private Date safelyParseDate(String dateAsString) {
        try {
            return timestampFormat.parse(dateAsString);
        } catch (ParseException e) {
            // the purpose of this method is to find valid date formats
            return null;
        }
    }

    private Path getOriginalLogFilePath() {
        return Paths.get(logger.getLogFilePath());
    }

    protected String getLogReformattingFilePath() {
        return Utils.computeLogReformattingFilePath(logger.getLogFilePath(), loggingConfig.getLogEcsFormattingDestinationDir());
    }

    private void verifyEcsFormat(String[] splitRawLogLine, JsonNode ecsLogLineTree) throws Exception {
        Date rawTimestamp = timestampFormat.parse(splitRawLogLine[0]);
        Date ecsTimestamp = utcTimestampFormat.parse(ecsLogLineTree.get("@timestamp").textValue());
        assertThat(rawTimestamp).isEqualTo(ecsTimestamp);
        if (logsThreadName()) {
            // JUL simple formatter doesn't have the capability to log the thread name
            // we've faked it with a 'main' in the format, but that no longer works
            // with parallelized unit tests (where the thread is a pool thread)
            assertThat(splitRawLogLine[1]).isEqualTo(ecsLogLineTree.get("process.thread.name").textValue());
        }
        JsonNode logLevel = ecsLogLineTree.get("log.level");
        assertThat(splitRawLogLine[2]).isEqualTo(logLevel.textValue());
        boolean isErrorLine = logLevel.textValue().equalsIgnoreCase("error");
        assertThat(splitRawLogLine[3]).isEqualTo(ecsLogLineTree.get("log.logger").textValue());
        assertThat(splitRawLogLine[4]).isEqualTo(ecsLogLineTree.get("message").textValue());
        verifyTracingMetadata(ecsLogLineTree);
        verifyLogCorrelation(ecsLogLineTree, isErrorLine);
    }

    private void verifyLogCorrelation(JsonNode ecsLogLineTree, boolean isErrorLine) {
        if (isLogCorrelationSupported()) {
            JsonNode traceId = ecsLogLineTree.get(AbstractLogCorrelationHelper.TRACE_ID_MDC_KEY);
            assertThat(traceId).describedAs("Logging correlation does not work as expected: missing trace ID").isNotNull();
            assertThat(traceId.textValue()).isEqualTo(transaction.getTraceContext().getTraceId().toString());
            JsonNode transactionId = ecsLogLineTree.get(AbstractLogCorrelationHelper.TRANSACTION_ID_MDC_KEY);
            assertThat(transactionId).describedAs("Logging correlation does not work as expected: missing transaction ID").isNotNull();
            assertThat(transactionId.textValue()).isEqualTo(transaction.getTraceContext().getTransactionId().toString());
            verifyErrorCaptureAndCorrelation(isErrorLine, ecsLogLineTree);
        } else {
            assertThat(ecsLogLineTree.get(AbstractLogCorrelationHelper.TRACE_ID_MDC_KEY)).isNull();
            assertThat(ecsLogLineTree.get(AbstractLogCorrelationHelper.TRANSACTION_ID_MDC_KEY)).isNull();
        }
    }

    protected boolean isLogCorrelationSupported() {
        return true;
    }

    /**
     * Tests our log file rolling configurations to verify it works as expected. Currently we allow one backup file
     * (meaning - two log files at most) and the decision to roll is based on the {@code log_file_size} configuration.
     * Because of the way Logback and log4j2 make their rolling decision, this test uses a fixed-duration sleep, which
     * is a notorious way to make tests flaky. If that proves to be the case, this test can be disabled, as its
     * importance for regression testing is not crucial. It would be very useful if we decide to modify anything in
     * our logging configuration, for example - change the rolling decision strategy.
     *
     * @throws IOException thrown if we fail to read the shade log file
     */
    @Test
    public void testReformattedLogRolling() throws IOException {
        setEcsReformattingConfig(LogEcsReformatting.SHADE);
        initializeReformattingDir("rolling");
        doReturn(100L).when(loggingConfig).getLogFileSize();
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
        String ecsLogFilePath = getLogReformattingFilePath();
        // in JUL, the base file is also denoted with a number (0)
        if (ecsLogFilePath.endsWith(".0")) {
            ecsLogFilePath = ecsLogFilePath.substring(0, ecsLogFilePath.length() - 2);
        }
        if (!ecsLogFilePath.endsWith(".1")) {
            ecsLogFilePath = ecsLogFilePath + ".1";
        }
        ArrayList<JsonNode> jsonNodes = TestUtils.readJsonFile(ecsLogFilePath);
        assertThat(jsonNodes).hasSize(1);
    }

    protected abstract void waitForFileRolling();
}
