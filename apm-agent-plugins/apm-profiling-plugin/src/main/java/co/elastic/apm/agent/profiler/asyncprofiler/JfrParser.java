/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.profiler.asyncprofiler;

import co.elastic.apm.agent.impl.transaction.StackFrame;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.Buffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class JfrParser {

    private static final Logger logger = LoggerFactory.getLogger(JfrParser.class);

    private static final byte[] MAGIC_BYTES = new byte[]{'F', 'L', 'R', '\0'};
    private static final Set<String> JAVA_FRAME_TYPES = new HashSet<>(Arrays.asList("Interpreted", "JIT compiled", "Inlined"));

    private final MappedByteBuffer buffer;
    private final short major;
    private final short minor;
    private final int eventsOffset;
    private boolean[] isJavaFrameType;
    private String[] threadStates;
    private final Map<Integer, Integer> classes = new HashMap<>();
    private final Map<Integer, Symbol> symbols = new HashMap<>();
    private final Map<Integer, Integer> stackTracePositions = new HashMap<>();
    private final Map<Long, LazyStackFrame> frames = new HashMap<>();
    private final int metadataOffset;
    private final StringBuilder symbolBuilder = new StringBuilder();

    public JfrParser(File file) throws IOException {
        FileChannel channel = new RandomAccessFile(file, "r").getChannel();
        logger.info("Parsing {} ({} bytes)", file, channel.size());
        if (channel.size() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Input file too large");
        }
        this.buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        for (byte magicByte : MAGIC_BYTES) {
            if (buffer.get() != magicByte) {
                throw new IllegalArgumentException("Not a JFR file");
            }
        }
        major = buffer.getShort();
        minor = buffer.getShort();
        if (major != 0 && minor != 9) {
            throw new IllegalArgumentException(String.format("Can only parse version 0.9. Was %d.%d", (int) major, (int) minor));
        }
        metadataOffset = (int) buffer.getLong();
        eventsOffset = buffer.position();

        setPosition(buffer, metadataOffset);
        int checkpointOffset = parseMetadata(buffer);
        setPosition(buffer, checkpointOffset);
        parseCheckpoint(buffer);
    }

    private int parseMetadata(MappedByteBuffer buffer) throws IOException {
        int size = buffer.getInt();
        expectEventType(buffer, EventTypeId.EVENT_METADATA);
        int checkpointOffsetPosition = size - 16;
        setPosition(buffer, buffer.position() + checkpointOffsetPosition);
        return (int) buffer.getLong();
    }

    private void expectEventType(MappedByteBuffer buffer, int expectedEventType) throws IOException {
        int eventType = buffer.getInt();
        if (eventType != expectedEventType) {
            throw new IOException("Expected " + expectedEventType + " but got " + eventType);
        }
    }

    private void parseCheckpoint(MappedByteBuffer buffer) throws IOException {
        buffer.getInt(); // size
        expectEventType(buffer, EventTypeId.EVENT_CHECKPOINT);
        buffer.getLong(); // stop timestamp
        buffer.getLong(); // previous checkpoint - always 0 in async-profiler
        while (buffer.position() < metadataOffset) {
            parseContentType(buffer);
        }
    }

    private void parseContentType(MappedByteBuffer buffer) throws IOException {
        int contentTypeId = buffer.getInt();
        logger.debug("Parsing content type {}", contentTypeId);
        int count = buffer.getInt();
        switch (contentTypeId) {
            case ContentTypeId.CONTENT_THREAD:
                // currently no thread info
                break;
            case ContentTypeId.CONTENT_STACKTRACE:
                for (int i = 0; i < count; i++) {
                    int pos = buffer.position();
                    int stackTraceKey = (int) buffer.getLong();
                    this.stackTracePositions.put(stackTraceKey, pos);
                    buffer.get(); // truncated
                    int numFrames = buffer.getInt();
                    setPosition(buffer, buffer.position() + numFrames * 13);
                }
                break;
            case ContentTypeId.CONTENT_CLASS:
                for (int i = 0; i < count; i++) {
                    long classId = buffer.getLong();
                    buffer.getLong(); // loader class
                    classes.put((int) classId, (int) buffer.getLong()); // class name
                    buffer.getShort(); // access flags
                }
                break;
            case ContentTypeId.CONTENT_METHOD:
                for (int i = 1; i <= count; i++) {
                    long id = buffer.getLong();
                    long classRef = buffer.getLong();
                    long methodName = buffer.getLong();
                    frames.put(id, new LazyStackFrame((int) classRef, (int) methodName));
                    buffer.getLong(); // signature
                    buffer.getShort(); // modifiers
                    buffer.get(); // hidden
                }
                break;
            case ContentTypeId.CONTENT_SYMBOL:
                for (int i = 0; i < count; i++) {
                    long id = buffer.getLong();
                    int pos = buffer.position();
                    symbols.put((int) id, new Symbol(pos));
                    skipString();
                }
                break;
            case ContentTypeId.CONTENT_STATE:
                threadStates = new String[count + 1];
                for (int i = 1; i <= count; i++) {
                    short id = buffer.getShort();
                    assert i == id;
                    threadStates[i] = readUtf8String();
                }
                break;
            case ContentTypeId.CONTENT_FRAME_TYPE:
                isJavaFrameType = new boolean[count + 1];
                for (int i = 1 ; i <= count; i++) {
                    byte id = buffer.get();
                    assert i == id;
                    isJavaFrameType[(int) id] = JAVA_FRAME_TYPES.contains(readUtf8String());
                }
                break;
            default:
                throw new IOException("Unknown content type " + contentTypeId);
        }
    }

    short getMajor() {
        return major;
    }

    short getMinor() {
        return minor;
    }

    private void skipString() {
        short stringLength = buffer.getShort();
        setPosition(buffer, buffer.position() + stringLength);
    }

    public void consumeStackTraces(StackTraceConsumer callback) throws IOException {
        setPosition(buffer, eventsOffset);
        while (buffer.position() < metadataOffset) {
            int size = buffer.getInt();
            int eventType = buffer.getInt();
            if (eventType == EventTypeId.EVENT_RECORDING) {
                return;
            }
            if (eventType != EventTypeId.EVENT_EXECUTION_SAMPLE){
                throw new IOException("Expected " + EventTypeId.EVENT_EXECUTION_SAMPLE + " but got " + eventType);
            }
            long nanoTime = buffer.getLong();
            int tid = buffer.getInt();
            long stackTraceId = buffer.getLong();
            short threadState = buffer.getShort();
            callback.onCallTree(tid, stackTraceId, nanoTime);
        }
    }

    public interface StackTraceConsumer {

        void onCallTree(int threadId, long stackTraceId, long nanoTime);
    }

    public void getStackTrace(long stackTraceId, boolean onlyJavaFrames, List<StackFrame> stackFrames, List<WildcardMatcher> excludedClasses, List<WildcardMatcher> includedClasses) {
        MappedByteBuffer buffer = this.buffer;
        int position = buffer.position();
        setPosition(buffer, stackTracePositions.get((int) stackTraceId));
        long stackTraceIdFromFile = buffer.getLong();
        assert stackTraceId == stackTraceIdFromFile;
        buffer.get(); // truncated
        int numFrames = buffer.getInt();
        for (int i = 0; i < numFrames; i++) {
            long method = (int) buffer.getLong();
            buffer.getInt(); // bci (always set to 0 by async-profiler)
            byte frameType = buffer.get();
            if (!onlyJavaFrames || isJavaFrameType(frameType)) {
                LazyStackFrame lazyStackFrame = frames.get(method);
                StackFrame stackFrame = lazyStackFrame.getStackFrame(this);
                if (isIncluded(stackFrame, excludedClasses, includedClasses)) {
                    stackFrames.add(stackFrame);
                }
            }
        }
        setPosition(buffer, position);
    }

    private static boolean isIncluded(StackFrame stackFrame, List<WildcardMatcher> excludedClasses, List<WildcardMatcher> includedClasses) {
        return WildcardMatcher.isAnyMatch(includedClasses, stackFrame.getClassName()) && WildcardMatcher.isNoneMatch(excludedClasses, stackFrame.getClassName());
    }

    private boolean isJavaFrameType(byte frameType) {
        return isJavaFrameType[frameType];
    }

    private String resolveSymbol(int pos, boolean replaceSlashWithDot) {
        int currentPos = buffer.position();
        setPosition(buffer, pos);
        try {
            return readUtf8String(replaceSlashWithDot);
        } finally {
            setPosition(buffer, currentPos);
        }
    }

    private static void setPosition(MappedByteBuffer buffer, int pos) {
        ((Buffer) buffer).position(pos);
    }

    private StackFrame resolveStackFrame(int classId, int methodName) {
        String className = symbols.get(classes.get(classId)).resolveClassName(this);
        String method = symbols.get(methodName).resolve(this);
        return new StackFrame(className, Objects.requireNonNull(method));
    }

    private String readUtf8String() {
        return readUtf8String(false);
    }

    private String readUtf8String(boolean replaceSlashWithDot) {
        int size = buffer.getShort();
        StringBuilder symbolBuilder = this.symbolBuilder;
        symbolBuilder.setLength(0);
        for (int i = 0; i < size; i++) {
            char c = (char) buffer.get();
            if (replaceSlashWithDot && c == '/') {
                symbolBuilder.append('.');
            } else {
                symbolBuilder.append(c);
            }
        }
        return symbolBuilder.toString();
    }

    private interface EventTypeId {
        int EVENT_METADATA           = 0;
        int EVENT_CHECKPOINT         = 1;
        int EVENT_RECORDING          = 10;
        int EVENT_RECORDING_SETTINGS = 11;
        int EVENT_EXECUTION_SAMPLE   = 20;

    };
    private interface ContentTypeId {
        int CONTENT_THREAD      = 7;
        int CONTENT_STACKTRACE  = 9;
        int CONTENT_CLASS       = 10;
        int CONTENT_METHOD      = 32;
        int CONTENT_SYMBOL      = 33;
        int CONTENT_STATE       = 34;
        int CONTENT_FRAME_TYPE  = 47;
    }

    private interface ThreadStateId {
        int STATE_RUNNABLE    = 1;
        int STATE_TOTAL_COUNT = 1;
    }

    private static class LazyStackFrame {
        private final int classId;
        private final int methodName;

        @Nullable
        private StackFrame stackFrame;

        public LazyStackFrame(int classId, int methodName) {
            this.classId = classId;
            this.methodName = methodName;
        }

        public StackFrame getStackFrame(JfrParser parser) {
            if (stackFrame == null) {
                stackFrame = parser.resolveStackFrame(classId, methodName);
            }
            return stackFrame;
        }
    }

    private static class Symbol {
        private final int pos;
        @Nullable
        private String resolved;

        private Symbol(int pos) {
            this.pos = pos;
        }

        @Nullable
        public String resolve(JfrParser parser) {
            return resolve(parser, false);
        }

        @Nullable
        private String resolveClassName(JfrParser parser) {
            return resolve(parser, true);
        }

        @Nullable
        private String resolve(JfrParser parser, boolean replaceSlashWithDot) {
            if (resolved == null) {
                resolved = parser.resolveSymbol(pos, replaceSlashWithDot);
            }
            if (resolved.isEmpty()) {
                return null;
            }
            return resolved;
        }
    }
}
