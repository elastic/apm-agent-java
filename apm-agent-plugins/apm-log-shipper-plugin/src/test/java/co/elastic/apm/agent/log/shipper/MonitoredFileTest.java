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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;


class MonitoredFileTest {

    private ListFileChangeListener logListener = new ListFileChangeListener();
    private MonitoredFile monitoredFile;

    @BeforeEach
    void setUp() throws Exception {
        monitoredFile = new MonitoredFile(new File(getClass().getResource("/log.log").toURI()));
    }

    @Test
    void testReadLogOneLine() throws IOException {
        monitoredFile.poll(ByteBuffer.allocate(1024), logListener, 1);
        assertThat(logListener.lines).hasSize(1);
        assertThat(logListener.lines.get(0)).isEqualTo("foo".getBytes());
    }

    @Test
    void testReadLog() throws IOException {
        monitoredFile.poll(ByteBuffer.allocate(1024), logListener, 5);
        assertThat(logListener.lines).hasSize(4);
        assertThat(logListener.lines.get(0)).isEqualTo("foo".getBytes());
        assertThat(logListener.lines.get(1)).isEqualTo("bar".getBytes());
        assertThat(logListener.lines.get(2)).isEqualTo("baz".getBytes());
        assertThat(logListener.lines.get(3)).isEqualTo("qux".getBytes());
    }

    @Test
    void testIndexOfNewLine() {
        byte[] bytes = {'c', 'a', 'f', 'e', '\n', 'b', 'a', 'b', 'e', '\r', '\n'};
        assertThat(MonitoredFile.indexOf(bytes, (byte) '\n', 0, bytes.length)).isEqualTo(4);
        assertThat(MonitoredFile.indexOf(bytes, (byte) '\n', 4, bytes.length)).isEqualTo(4);
        assertThat(MonitoredFile.indexOf(bytes, (byte) '\n', 5, bytes.length)).isEqualTo(10);
    }

    @Test
    void testReadOneLine() throws IOException {
        byte[] bytes = {'c', 'a', 'f', 'e', '\n', 'b', 'a', 'b', 'e', '\r', '\n'};
        MonitoredFile.readLines(new File("foo.log"), bytes, bytes.length, 1, logListener);
        assertThat(logListener.lines).hasSize(1);
        assertThat(logListener.lines.get(0)).isEqualTo(new byte[]{'c', 'a', 'f', 'e'});
    }

    @Test
    void testRetry() throws IOException {
        byte[] bytes = {'c', 'a', 'f', 'e', '\n'};
        List<byte[]> readBytes = new ArrayList<>();
        AtomicBoolean processingSuccessful = new AtomicBoolean(false);
        MonitoredFile.readLines(new File("foo.log"), bytes, bytes.length, 1, new FileChangeListenerAdapter() {
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
        byte[] bytes = {'c', 'a', 'f', 'e', '\n', 'b', 'a', 'b', 'e', '\r', '\n'};
        MonitoredFile.readLines(new File("foo.log"), bytes, bytes.length, 4, logListener);
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
