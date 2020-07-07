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
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class LogShadingInstrumentationTest extends AbstractInstrumentationTest {

    public static final String TRACE_MESSAGE = "Trace-this";
    public static final String DEBUG_MESSAGE = "Debug-this";
    public static final String WARN_MESSAGE = "Warn-this";
    public static final String ERROR_MESSAGE = "Error-this";

    public static final String SERVICE_NAME = "ECS Logging Test";

    private final LoggerFacade logger;
    private ObjectMapper objectMapper = new ObjectMapper();

    public LogShadingInstrumentationTest(LoggerFacade logger) {
        this.logger = logger;
    }

    @Before
    public void setup() throws IOException {
        when(config.getConfig(CoreConfiguration.class).getServiceName()).thenReturn(SERVICE_NAME);
        Files.deleteIfExists(Paths.get(getShadeLogFilePath()));
    }

    @Parameterized.Parameters(name = "LoggerFacade = {0}")
    public static Iterable<LoggerFacade> brokerFacades() {
        return Arrays.asList(new LogbackLoggerFacade(), new Log4j2LoggerFacade());
    }

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

        ArrayList<String[]> rawLogLines;
        try (Stream<String> stream = Files.lines(Paths.get(logger.getLogFilePath()))) {
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
        return Utils.computeShadeLogFilePath(logger.getLogFilePath());
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
//     @Test
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
