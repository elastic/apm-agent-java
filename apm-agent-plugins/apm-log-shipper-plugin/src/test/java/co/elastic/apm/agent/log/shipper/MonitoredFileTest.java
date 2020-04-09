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
package co.elastic.apm.agent.log.shipper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.assertj.core.api.Assertions.assertThat;


class MonitoredFileTest {

    private final ByteBuffer buffy = ByteBuffer.allocate(1024);
    private File logFile;
    private ListFileChangeListener logListener = new ListFileChangeListener();
    private MonitoredFile monitoredFile;

    @BeforeEach
    void setUp() throws Exception {
        logFile = new File(getClass().getResource("/log.log").toURI());
        monitoredFile = new MonitoredFile(logFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        monitoredFile.close();
        monitoredFile.deleteState();
    }

    @Test
    void testReadLogOneLine() throws IOException {
        monitoredFile.poll(buffy, logListener, 1);
        assertThat(logListener.lines).hasSize(1);
        assertThat(logListener.lines.get(0)).isEqualTo("foo".getBytes());
    }

    @Test
    void testReadLogTwoTimesOneLine() throws IOException {
        monitoredFile.poll(buffy, logListener, 1);
        assertThat(logListener.lines).hasSize(1);
        assertThat(logListener.lines.get(0)).isEqualTo("foo".getBytes());

        monitoredFile.poll(buffy, logListener, 1);
        assertThat(logListener.lines).hasSize(2);
        assertThat(logListener.lines.get(1)).isEqualTo("bar".getBytes());
    }

    @Test
    void testRestoreState() throws Exception {
        monitoredFile.poll(buffy, logListener, 1);
        assertThat(logListener.lines).hasSize(1);
        assertThat(logListener.lines.get(0)).isEqualTo("foo".getBytes());
        monitoredFile.close();

        setUp();
        monitoredFile.poll(buffy, logListener, 1);
        assertThat(logListener.lines).hasSize(2);
        assertThat(logListener.lines.get(1)).isEqualTo("bar".getBytes());
    }

    @Test
    void testRestoreStateOfRotatedFile() throws Exception {
        monitoredFile.poll(buffy, logListener, 1);
        assertThat(logListener.lines).hasSize(1);
        assertThat(logListener.lines.get(0)).isEqualTo("foo".getBytes());
        monitoredFile.close();

        Path rotatedLog = logFile.toPath().resolveSibling("log-1.log");
        Files.move(logFile.toPath(), rotatedLog);
        try {
            Files.write(logFile.toPath(), List.of("quux", "corge"), CREATE_NEW);

            setUp();
            monitoredFile.poll(buffy, logListener, 10);
            assertThat(logListener.lines).hasSize(6);
            // continue reading form old rotated log
            assertThat(logListener.lines.get(1)).isEqualTo("bar".getBytes());
            assertThat(logListener.lines.get(2)).isEqualTo("baz".getBytes());
            assertThat(logListener.lines.get(3)).isEqualTo("qux".getBytes());
            // resume with new log
            assertThat(logListener.lines.get(4)).isEqualTo("quux".getBytes());
            assertThat(logListener.lines.get(5)).isEqualTo("corge".getBytes());
        } finally {
            Files.move(rotatedLog, logFile.toPath(), REPLACE_EXISTING);
        }
    }

    @Test
    void testReadLog() throws IOException {
        monitoredFile.poll(buffy, logListener, 5);
        assertThat(logListener.lines).hasSize(4);
        assertThat(logListener.lines.get(0)).isEqualTo("foo".getBytes());
        assertThat(logListener.lines.get(1)).isEqualTo("bar".getBytes());
        assertThat(logListener.lines.get(2)).isEqualTo("baz".getBytes());
        assertThat(logListener.lines.get(3)).isEqualTo("qux".getBytes());
    }

    @Test
    void testIndexOfNewLine() {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[]{'c', 'a', 'f', 'e', '\n', 'b', 'a', 'b', 'e', '\r', '\n'});
        assertThat(MonitoredFile.skipUntil(buffer, (byte) '\n')).isTrue();
        assertThat(buffer.position()).isEqualTo(5);
        assertThat(MonitoredFile.skipUntil(buffer, (byte) '\n')).isTrue();
        assertThat(buffer.position()).isEqualTo(11);
        assertThat(MonitoredFile.skipUntil(buffer, (byte) '\n')).isFalse();
    }

    @Test
    void testReadOneLine() throws IOException {
        ByteBuffer bytes = ByteBuffer.wrap(new byte[]{'c', 'a', 'f', 'e', '\n', 'b', 'a', 'b', 'e', '\r', '\n'});
        MonitoredFile.readLines(new File("foo.log"), bytes, 1, logListener);
        assertThat(logListener.lines).hasSize(1);
        assertThat(logListener.lines.get(0)).isEqualTo(new byte[]{'c', 'a', 'f', 'e'});
    }

    @Test
    void testRetry() throws IOException {
        ByteBuffer bytes = ByteBuffer.wrap(new byte[]{'c', 'a', 'f', 'e', '\n'});
        List<byte[]> readBytes = new ArrayList<>();
        AtomicBoolean processingSuccessful = new AtomicBoolean(false);
        MonitoredFile.readLines(new File("foo.log"), bytes, 1, new FileChangeListenerAdapter() {
            @Override
            public boolean onLineAvailable(File file, byte[] line, int offset, int length, boolean eol) {
                readBytes.add(Arrays.copyOfRange(line, offset, offset + length));
                return processingSuccessful.getAndSet(true);
            }
        });
        assertThat(readBytes).hasSize(2);
        assertThat(readBytes.get(0)).isEqualTo(new byte[]{'c', 'a', 'f', 'e'});
        assertThat(readBytes.get(1)).isEqualTo(new byte[]{'c', 'a', 'f', 'e'});
    }

    @Test
    void testReadTwoLines() throws IOException {
        ByteBuffer bytes = ByteBuffer.wrap(new byte[]{'c', 'a', 'f', 'e', '\n', 'b', 'a', 'b', 'e', '\r', '\n'});
        MonitoredFile.readLines(new File("foo.log"), bytes, 4, logListener);
        assertThat(logListener.lines).hasSize(2);
        assertThat(logListener.lines.get(0)).isEqualTo(new byte[]{'c', 'a', 'f', 'e'});
        assertThat(logListener.lines.get(1)).isEqualTo(new byte[]{'b', 'a', 'b', 'e'});
    }

    private static class ListFileChangeListener extends FileChangeListenerAdapter {
        private final List<byte[]> lines = new ArrayList<>();

        @Override
        public boolean onLineAvailable(File file, byte[] line, int offset, int length, boolean eol) {
            lines.add(Arrays.copyOfRange(line, offset, offset + length));
            return true;
        }
    }
}
