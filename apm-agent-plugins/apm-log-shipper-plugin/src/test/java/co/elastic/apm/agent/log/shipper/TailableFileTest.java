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
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisabledOnOs(OS.WINDOWS)
class TailableFileTest {

    private final ByteBuffer buffy = ByteBuffer.allocate(1024);
    private File logFile;
    private ListFileChangeListener logListener = new ListFileChangeListener();
    private TailableFile tailableFile;

    @BeforeEach
    void setUp() throws Exception {
        logFile = new File(getClass().getResource("/log.log").toURI());
        tailableFile = new TailableFile(logFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        tailableFile.close();
        tailableFile.deleteStateFile();
    }

    @Test
    void testReadLogOneLine() throws Exception {
        tailableFile.tail(buffy, logListener, 1);
        assertThat(logListener.lines).hasSize(1);
        assertThat(logListener.lines.get(0)).isEqualTo("foo");
    }

    @Test
    void testReadLogTwoTimesOneLine() throws Exception {
        tailableFile.tail(buffy, logListener, 1);
        assertThat(logListener.lines).hasSize(1);
        assertThat(logListener.lines.get(0)).isEqualTo("foo");

        tailableFile.tail(buffy, logListener, 1);
        assertThat(logListener.lines).hasSize(2);
        assertThat(logListener.lines.get(1)).isEqualTo("bar");
    }

    @Test
    void testRestoreState() throws Exception {
        tailableFile.tail(buffy, logListener, 1);
        assertThat(logListener.lines).hasSize(1);
        assertThat(logListener.lines.get(0)).isEqualTo("foo");
        tailableFile.ack();
        tailableFile.close();

        setUp();
        tailableFile.tail(buffy, logListener, 1);
        assertThat(logListener.lines).hasSize(2);
        assertThat(logListener.lines.get(1)).isEqualTo("bar");
    }

    @Test
    void testNegativeAcknowledge() throws Exception {
        tailableFile.tail(buffy, logListener, 1);
        assertThat(logListener.lines).containsExactly("foo");

        tailableFile.nak();

        tailableFile.tail(buffy, logListener, 1);
        assertThat(logListener.lines).containsExactly("foo", "foo");
    }

    @Test
    void testFileLock() {
        assertThatThrownBy(() -> new TailableFile(logFile))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageStartingWith("This file is currently locked by this process");
    }

    @Test
    void testRestoreStateOfRotatedFile() throws Exception {
        tailableFile.tail(buffy, logListener, 1);
        assertThat(logListener.lines).containsExactly("foo");
        assertThat(logListener.lines.get(0)).isEqualTo("foo");
        tailableFile.ack();
        tailableFile.close();
        logListener.lines.clear();

        Path rotatedLog = logFile.toPath().resolveSibling("log-1.log");
        Files.move(logFile.toPath(), rotatedLog);
        try {
            Files.write(logFile.toPath(), List.of("quux", "corge"), CREATE_NEW);

            setUp();
            tailableFile.tail(buffy, logListener, 10);
            assertThat(logListener.lines).containsExactly(
                // continue reading form old rotated log
                "bar", "baz", "qux",
                // resume with new log
                "quux", "corge");
        } finally {
            Files.move(rotatedLog, logFile.toPath(), REPLACE_EXISTING);
        }
    }

    @Test
    void testFileRotationBeforeAck() throws Exception {
        Path rotatedLog = logFile.toPath().resolveSibling("log-1.log");
        Files.move(logFile.toPath(), rotatedLog);
        try {
            Files.write(logFile.toPath(), List.of("quux", "corge"), CREATE_NEW);

            tailableFile.tail(buffy, logListener, 10);
            assertThat(logListener.lines).containsExactly(
                // start continue reading form old rotated log
                "foo", "bar", "baz", "qux",
                // resume with new log
                "quux", "corge");
        } finally {
            Files.move(rotatedLog, logFile.toPath(), REPLACE_EXISTING);
        }
    }

    @Test
    void testReadLog() throws Exception {
        assertThat(logFile.length()).isGreaterThan(0);
        tailableFile.tail(buffy, logListener, 5);
        assertThat(logListener.lines).containsExactly("foo", "bar", "baz", "qux");
    }

    @Test
    void testNonExistingFile() throws Exception {
        TailableFile file = new TailableFile(logFile.toPath().getParent().resolve("404.log").toFile());
        try (file) {
            file.tail(buffy, logListener, 5);
            assertThat(logListener.lines).isEmpty();
        } finally {
            file.deleteStateFile();
        }
    }

    @Test
    void testInitiallyNonExistingFile() throws Exception {
        TailableFile file = new TailableFile(logFile.toPath().getParent().resolve("404.log").toFile());
        try (file) {
            file.tail(buffy, logListener, 5);
            assertThat(logListener.lines).isEmpty();

            Files.write(file.getFile().toPath(), List.of("foo", "bar"), CREATE_NEW);
            file.tail(buffy, logListener, 5);
            assertThat(logListener.lines).containsExactly("foo", "bar");
            assertThat(new File(file.getFile() + ".state").length()).isGreaterThan(0);
        } finally {
            file.deleteStateFile();
            file.getFile().delete();
        }
    }

    @Test
    void testIndexOfNewLine() {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[]{'c', 'a', 'f', 'e', '\n', 'b', 'a', 'b', 'e', '\r', '\n'});
        assertThat(TailableFile.skipUntil(buffer, (byte) '\n')).isTrue();
        assertThat(buffer.position()).isEqualTo(5);
        assertThat(TailableFile.skipUntil(buffer, (byte) '\n')).isTrue();
        assertThat(buffer.position()).isEqualTo(11);
        assertThat(TailableFile.skipUntil(buffer, (byte) '\n')).isFalse();
    }

    @Test
    void testReadOneLine() throws Exception {
        ByteBuffer bytes = ByteBuffer.wrap(new byte[]{'c', 'a', 'f', 'e', '\n', 'b', 'a', 'b', 'e', '\r', '\n'});
        TailableFile.readLines(tailableFile, bytes, 1, logListener);
        assertThat(logListener.lines).containsExactly("cafe");
    }

    @Test
    void testRetry() throws Exception {
        ByteBuffer bytes = ByteBuffer.wrap(new byte[]{'c', 'a', 'f', 'e', '\n'});
        List<String> readBytes = new ArrayList<>();
        AtomicBoolean processingSuccessful = new AtomicBoolean(false);
        TailableFile.readLines(tailableFile, bytes, 1, new FileChangeListenerAdapter() {
            @Override
            public boolean onLineAvailable(TailableFile file, byte[] line, int offset, int length, boolean eol) {
                readBytes.add(new String(line, offset, length));
                return processingSuccessful.getAndSet(true);
            }
        });
        assertThat(readBytes).containsExactly("cafe", "cafe");
    }

    @Test
    void testReadTwoLines() throws Exception {
        ByteBuffer bytes = ByteBuffer.wrap(new byte[]{'c', 'a', 'f', 'e', '\n', 'b', 'a', 'b', 'e', '\r', '\n'});
        TailableFile.readLines(tailableFile, bytes, 4, logListener);
        assertThat(logListener.lines).containsExactly("cafe", "babe");
    }

    private static class ListFileChangeListener extends FileChangeListenerAdapter {
        private final List<String> lines = new ArrayList<>();

        @Override
        public boolean onLineAvailable(TailableFile file, byte[] line, int offset, int length, boolean eol) {
            lines.add(new String(line, offset, length));
            return true;
        }
    }
}
