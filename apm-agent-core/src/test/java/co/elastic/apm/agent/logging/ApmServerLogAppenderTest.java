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
package co.elastic.apm.agent.logging;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.report.ApmServerReporter;
import co.elastic.logging.log4j2.EcsLayout;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ApmServerLogAppenderTest {

    private static final Instant BASE_CLOCK = Instant.now();

    @Nullable
    private Object previousInstanceValue;
    private Field instanceField;

    @BeforeEach
    public void before() throws Exception {
        // use introspection to reset the instance to a known null value, while perserving the original value
        instanceField = ApmServerLogAppender.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        previousInstanceValue = instanceField.get(null);
        instanceField.set(null, null);
    }

    @AfterEach
    public void after() throws Exception {
        instanceField.set(null, previousInstanceValue);
    }

    @Test
    void createAndInit() throws Exception {
        assertThatThrownBy(() -> ApmServerLogAppender.createAppender("test", mock(Layout.class)),
            "non ECS layout should not be allowed");

        EcsLayout layout = mock(EcsLayout.class);

        ApmServerLogAppender appender = ApmServerLogAppender.createAppender("test", layout);
        assertThat(appender.getName()).isEqualTo("test");

        ElasticApmTracer tracer = MockTracer.create();
        ApmServerReporter reporter = mock(ApmServerReporter.class);
        doReturn(reporter).when(tracer).getReporter();

        LoggingConfigurationImpl config = tracer.getConfig(LoggingConfigurationImpl.class);
        doReturn(true).when(config).getSendLogs();
        appender.getInitListener().init(tracer);

        assertThatThrownBy(() -> appender.getInitListener().init(tracer), "should throw when trying to init more than once");

        // testing singleton invariants, while being implementation details matter here
        assertThat(ApmServerLogAppender.getInstance())
            .describedAs("singleton instance should be set by the factory")
            .isSameAs(appender);
        assertThat(ApmServerLogAppender.createAppender("test", layout))
            .describedAs("factory method should only create once and return the singleton afterwards")
            .isSameAs(appender);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bufferingAndInit(boolean enabled) throws Exception {

        EcsLayout layout = fakeLayout();

        // using constructor to avoid singleton that won't allow more than one instance
        ApmServerLogAppender appender = new ApmServerLogAppender("test", layout);
        ApmServerReporter reporter = mock(ApmServerReporter.class);

        int expectedBufferSize = 1024;
        for (int i = 0; i < expectedBufferSize + 1; i++) {
            appender.append(fakeLogEvent(i));
        }

        verifyNoInteractions(reporter);

        ElasticApmTracer tracer = MockTracer.create();
        LoggingConfigurationImpl config = tracer.getConfig(LoggingConfigurationImpl.class);
        doReturn(enabled).when(config).getSendLogs();
        doReturn(reporter).when(tracer).getReporter();

        appender.getInitListener().init(tracer);

        if (!enabled) {
            // when disabled no call to reported is expected with buffered events
            verifyNoInteractions(reporter);
        } else {
            // when enabled buffered events should be sent to reporter
            ArgumentCaptor<byte[]> logEventsCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(reporter, times(expectedBufferSize)).reportAgentLog(logEventsCaptor.capture());

            List<byte[]> serializedEventsList = logEventsCaptor.getAllValues();
            assertThat(serializedEventsList).hasSize(expectedBufferSize);

            List<String> eventsList = serializedEventsList.stream().map(String::new).collect(Collectors.toList());
            for (int i = 0; i < eventsList.size(); i++) {
                assertThat(eventsList.get(i)).isEqualTo(new String(layout.toByteArray(fakeLogEvent(i))));
            }
        }

        // now that init is done, no buffering is expected
        appender.append(fakeLogEvent(2048));
        if (!enabled) {
            verifyNoInteractions(reporter);
        } else {
            // this capture will also include the buffered events, the post-init reported event is expected to be the last
            ArgumentCaptor<byte[]> logEventsCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(reporter, times(expectedBufferSize + 1)).reportAgentLog(logEventsCaptor.capture());

            assertThat(logEventsCaptor.getAllValues()).hasSize(expectedBufferSize + 1);
            assertThat(logEventsCaptor.getAllValues().get(expectedBufferSize)).isEqualTo(layout.toByteArray(fakeLogEvent(2048)));
        }

    }


    private EcsLayout fakeLayout() {
        return EcsLayout.newBuilder().build();
    }

    private static LogEvent fakeLogEvent(int index) {
        return new Log4jLogEvent().asBuilder()
            .setTimeMillis(BASE_CLOCK.plus(1, ChronoUnit.SECONDS).toEpochMilli())
            .setMessage(new SimpleMessage("fake log event " + index))
            .build();
    }

}
