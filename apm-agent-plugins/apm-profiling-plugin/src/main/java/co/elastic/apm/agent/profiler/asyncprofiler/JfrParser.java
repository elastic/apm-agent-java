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
import co.elastic.apm.agent.objectpool.Recyclable;
import co.elastic.apm.agent.profiler.collections.Int2IntHashMap;
import co.elastic.apm.agent.profiler.collections.Int2ObjectHashMap;
import co.elastic.apm.agent.profiler.collections.Long2ObjectHashMap;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Parses the binary JFR file created by async-profiler.
 * May not work with JFR files created by an actual flight recorder.
 * <p>
 * The implementation is tuned with to minimize allocations when parsing a JFR file.
 * Most data structures can be reused by first {@linkplain #resetState() resetting the state} and then {@linkplain #parse(File, List, List) parsing}
 * another file.
 * </p>
 * <p>
 * The JFR file itself is mapped into memory via a {@link MappedByteBuffer}.
 * This does not consume heap memory, the operating system loads pages the file into memory as they are requested.
 * Unfortunately, unmapping a {@link MappedByteBuffer} can only be done by letting it be garbage collected.
 * </p>
 */
public class JfrParser implements Recyclable {

    private static final Logger logger = LoggerFactory.getLogger(JfrParser.class);

    private static final byte[] MAGIC_BYTES = new byte[]{'F', 'L', 'R', '\0'};
    private static final Set<String> JAVA_FRAME_TYPES = new HashSet<>(Arrays.asList("Interpreted", "JIT compiled", "Inlined"));

    @Nullable
    private MappedByteBuffer buffer;
    private int eventsOffset;
    private int metadataOffset;
    @Nullable
    private boolean[] isJavaFrameType;
    private final Int2IntHashMap classIdToClassNameSymbolId = new Int2IntHashMap(-1);
    private final Int2ObjectHashMap<Symbol> symbols = new Int2ObjectHashMap<>();
    private final Int2IntHashMap stackTraceIdToFilePositions = new Int2IntHashMap(-1);
    private final Long2ObjectHashMap<LazyStackFrame> framesByFrameId = new Long2ObjectHashMap<>();
    // used to resolve a symbol with minimal allocations
    private final StringBuilder symbolBuilder = new StringBuilder();
    @Nullable
    private List<WildcardMatcher> excludedClasses;
    @Nullable
    private List<WildcardMatcher> includedClasses;

    /**
     * Initializes the parser to make it ready for {@link #getStackTrace(long, boolean, List)} to be called.
     *
     * @param file            the JFR file to parse
     * @param excludedClasses Class names to exclude in stack traces (has an effect on {@link #getStackTrace(long, boolean, List)})
     * @param includedClasses Class names to include in stack traces (has an effect on {@link #getStackTrace(long, boolean, List)})
     * @throws IOException if some I/O error occurs
     */
    public void parse(File file, List<WildcardMatcher> excludedClasses, List<WildcardMatcher> includedClasses) throws IOException {
        this.excludedClasses = excludedClasses;
        this.includedClasses = includedClasses;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            FileChannel channel = raf.getChannel();
            logger.info("Parsing {} ({} bytes)", file, channel.size());
            if (channel.size() > Integer.MAX_VALUE) {
                // mapping a file into memory is only possible for chunks of the file which fall into the Integer range (2GB)
                // that is because MappedByteBuffers are ByteBuffers which only accept ints in position(int pos)
                throw new IllegalArgumentException("Input file too large");
            }
            this.buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        }
        for (byte magicByte : MAGIC_BYTES) {
            if (buffer.get() != magicByte) {
                throw new IllegalArgumentException("Not a JFR file");
            }
        }
        short major = buffer.getShort();
        short minor = buffer.getShort();
        if (major != 0 && minor != 9) {
            throw new IllegalArgumentException(String.format("Can only parse version 0.9. Was %d.%d", major, minor));
        }
        // safe as we only process files where size <= Integer.MAX_VALUE
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
        // safe as we only process files where size <= Integer.MAX_VALUE
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
                    // always an integer
                    // see profiler.h
                    // MAX_CALLTRACES = 65536
                    int stackTraceKey = (int) buffer.getLong();
                    this.stackTraceIdToFilePositions.put(stackTraceKey, pos);
                    buffer.get(); // truncated
                    int numFrames = buffer.getInt();
                    int sizeOfFrame = 13;
                    setPosition(buffer, buffer.position() + numFrames * sizeOfFrame);
                }
                break;
            case ContentTypeId.CONTENT_CLASS:
                for (int i = 0; i < count; i++) {
                    // classId is an incrementing integer, no way there are more than 2 billion distinct ones
                    int classId = (int) buffer.getLong();
                    buffer.getLong(); // loader class
                    // symbol ids are incrementing integers, no way there are more than 2 billion distinct ones
                    int classNameSymbolId = (int) buffer.getLong();
                    classIdToClassNameSymbolId.put(classId, classNameSymbolId); // class name
                    buffer.getShort(); // access flags
                }
                break;
            case ContentTypeId.CONTENT_METHOD:
                for (int i = 1; i <= count; i++) {
                    long id = buffer.getLong();
                    // classId is an incrementing integer, no way there are more than 2 billion distinct ones
                    int classId = (int) buffer.getLong();
                    // symbol ids are incrementing integers, no way there are more than 2 billion distinct ones
                    int methodNameSymbolId = (int) buffer.getLong();
                    framesByFrameId.put(id, new LazyStackFrame(classId, methodNameSymbolId));
                    buffer.getLong(); // signature
                    buffer.getShort(); // modifiers
                    buffer.get(); // hidden
                }
                break;
            case ContentTypeId.CONTENT_SYMBOL:
                for (int i = 0; i < count; i++) {
                    // symbol ids are incrementing integers, no way there are more than 2 billion distinct ones
                    int symbolId = (int) buffer.getLong();
                    int pos = buffer.position();
                    symbols.put(symbolId, new Symbol(pos));
                    skipString();
                }
                break;
            case ContentTypeId.CONTENT_STATE:
                // we're not really interested in the thread states (async-profiler hard-codes state RUNNABLE) anyways
                // but we sill have to consume the bytes
                for (int i = 1; i <= count; i++) {
                    buffer.getShort();
                    skipString();
                }
                break;
            case ContentTypeId.CONTENT_FRAME_TYPE:
                isJavaFrameType = new boolean[count + 1];
                for (int i = 1 ; i <= count; i++) {
                    int id = buffer.get();
                    if (i != id) {
                        throw new IllegalStateException("Expecting ids to be incrementing");
                    }
                    isJavaFrameType[id] = JAVA_FRAME_TYPES.contains(readUtf8String().toString());
                }
                break;
            default:
                throw new IOException("Unknown content type " + contentTypeId);
        }
    }

    private void skipString() {
        short stringLength = buffer.getShort();
        setPosition(buffer, buffer.position() + stringLength);
    }

    /**
     * Invokes the callback for each stack trace event in the JFR file.
     *
     * @param callback called for each stack trace event
     * @throws IOException if some I/O error occurs
     */
    public void consumeStackTraces(StackTraceConsumer callback) throws IOException {
        if (buffer == null) {
            throw new IllegalStateException("consumeStackTraces was called before parse");
        }
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

        /**
         * @param threadId     The native thread id for with the event was recorded.
         *                     Note that this is not the same as {@link Thread#getId()}.
         * @param stackTraceId The id of the stack trace event.
         *                     Can be used to resolve the stack trace via {@link #getStackTrace(long, boolean, List)}
         * @param nanoTime     The timestamp of the event which can be correlated with {@link System#nanoTime()}
         */
        void onCallTree(int threadId, long stackTraceId, long nanoTime);
    }

    /**
     * Resolves the stack trace with the given {@code stackTraceId}.
     * <p>
     * Note that his allocates strings for {@link Symbol#resolved} in case a stack frame has not already been resolved for the current JFR file yet.
     * These strings are currently not cached so this can create some GC pressure.
     * </p>
     *
     * @param stackTraceId   The id of the stack traced.
     *                       Used to look up the position of the file in which the given stack trace is stored via {@link #stackTraceIdToFilePositions}.
     * @param onlyJavaFrames If {@code true}, will only resolve {@code Interpreted}, {@code JIT compiled} and {@code Inlined} frames.
     *                       If {@code false}, will also resolve {@code Native}, {@code Kernel} and {@code C++} frames.
     * @param stackFrames    The mutable list where the stack frames are written to.
     *                       Don't forget to {@link List#clear()} the list before calling this method if the list is reused.
     */
    public void getStackTrace(long stackTraceId, boolean onlyJavaFrames, List<StackFrame> stackFrames) {
        if (buffer == null) {
            throw new IllegalStateException("getStackTrace was called before parse");
        }
        MappedByteBuffer buffer = this.buffer;
        int position = buffer.position();
        setPosition(buffer, stackTraceIdToFilePositions.get((int) stackTraceId));
        long stackTraceIdFromFile = buffer.getLong();
        assert stackTraceId == stackTraceIdFromFile;
        buffer.get(); // truncated
        int numFrames = buffer.getInt();
        for (int i = 0; i < numFrames; i++) {
            long method = buffer.getLong();
            buffer.getInt(); // bci (always set to 0 by async-profiler)
            byte frameType = buffer.get();
            if (!onlyJavaFrames || isJavaFrameType(frameType)) {
                LazyStackFrame lazyStackFrame = framesByFrameId.get(method);
                if (lazyStackFrame.isIncluded(this)) {
                    StackFrame stackFrame = lazyStackFrame.resolve(this);
                    stackFrames.add(stackFrame);
                }
            }
        }
        setPosition(buffer, position);
    }

    private boolean isJavaFrameType(byte frameType) {
        return isJavaFrameType[frameType];
    }

    private StringBuilder resolveSymbol(int pos, boolean replaceSlashWithDot) {
        int currentPos = buffer.position();
        setPosition(buffer, pos);
        try {
            return readUtf8String(replaceSlashWithDot);
        } finally {
            setPosition(buffer, currentPos);
        }
    }

    private boolean isClassIncluded(CharSequence className) {
        return WildcardMatcher.isAnyMatch(includedClasses, className) && WildcardMatcher.isNoneMatch(excludedClasses, className);
    }

    private static void setPosition(MappedByteBuffer buffer, int pos) {
        // weird hack because of binary incompatibility introduced in Java 9
        // the return type was changed from Buffer to MappedByteBuffer
        // linking a method also incorporates the exact return type
        ((Buffer) buffer).position(pos);
    }

    private StackFrame resolveStackFrame(int classId, int methodName) {
        Symbol classNameSymbol = symbols.get(classIdToClassNameSymbolId.get(classId));
        if (classNameSymbol.isClassNameIncluded(this)) {
            String className = classNameSymbol.resolveClassName(this);
            String method = symbols.get(methodName).resolve(this);
            return new StackFrame(className, Objects.requireNonNull(method));
        } else {
            return LazyStackFrame.EXCLUDED;
        }
    }

    private StringBuilder readUtf8String() {
        return readUtf8String(false);
    }

    private StringBuilder readUtf8String(boolean replaceSlashWithDot) {
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
        return symbolBuilder;
    }

    @Override
    public void resetState() {
        buffer = null;
        eventsOffset = 0;
        metadataOffset = 0;
        isJavaFrameType = null;
        classIdToClassNameSymbolId.clear();
        symbols.clear();
        stackTraceIdToFilePositions.clear();
        framesByFrameId.clear();
        symbolBuilder.setLength(0);
        excludedClasses = null;
        includedClasses = null;
    }

    private interface EventTypeId {
        int EVENT_METADATA           = 0;
        int EVENT_CHECKPOINT         = 1;
        int EVENT_RECORDING          = 10;
        int EVENT_EXECUTION_SAMPLE   = 20;
    }

    private interface ContentTypeId {
        int CONTENT_THREAD      = 7;
        int CONTENT_STACKTRACE  = 9;
        int CONTENT_CLASS       = 10;
        int CONTENT_METHOD      = 32;
        int CONTENT_SYMBOL      = 33;
        int CONTENT_STATE       = 34;
        int CONTENT_FRAME_TYPE  = 47;
    }

    /**
     * Represents a single frame of a stack trace.
     * As stack frames are detached from stack traces, the same frame can occur in multiple stack traces.
     * That's why within a JFR file, a stack trace is basically represented as an array of pointers to stack frames.
     * <p>
     * The actual {@link StackFrame} is resolved lazily to avoid allocations when they are not needed.
     * That can be the case when not processing stack traces of particular threads, for example.
     * The resolved {@link #stackFrame} is then cached so that I/O is avoided when subsequently resolving the same frame.
     * </p>
     */
    private static class LazyStackFrame {

        private final static StackFrame EXCLUDED = new StackFrame("excluded", "excluded");

        private final int classId;
        private final int methodName;

        @Nullable
        private StackFrame stackFrame;

        public LazyStackFrame(int classId, int methodName) {
            this.classId = classId;
            this.methodName = methodName;
        }

        public StackFrame resolve(JfrParser parser) {
            if (stackFrame == null) {
                stackFrame = parser.resolveStackFrame(classId, methodName);
            }
            return stackFrame;
        }

        /**
         * Returns {@code true} when the class name matches the matchers provided via {@link #parse(File, List, List)}
         *
         * @param parser
         * @return
         */
        public boolean isIncluded(JfrParser parser) {
            return resolve(parser) != EXCLUDED;
        }
    }

    /**
     * A symbol is a UTF-8 string with an ID, representing a method name or class name, for example.
     * There's a specific section in the JFR file which contains all symbols.
     * <p>
     * Symbols are are {@link #resolve}d lazily to avoid allocations when they are not needed.
     * That can be the case when not processing stack traces of particular threads, for example.
     * The {@link #resolved} String is then cached so that I/O is avoided when subsequently resolving the same symbol.
     * </p>
     */
    private static class Symbol {
        private static final String EXCLUDED = "3x cluded";
        /**
         * The position in the JFR file which holds the symbol
         */
        private final int pos;
        @Nullable
        private String resolved;

        private Symbol(int pos) {
            this.pos = pos;
        }

        /**
         * Resolves a symbol representing a class name from the JFR file ({@link #buffer}).
         */
        @Nullable
        public String resolve(JfrParser parser) {
            return resolve(parser, false);
        }

        /**
         * Resolves a symbol representing a class name from the JFR file ({@link #buffer}).
         * <p>
         * In the JFR file, class names are in their binary form (for example {@code foo/bar/Baz}.
         * This methods converts it to the form matching {@link Class#getName()} by replacing the slashes with dots.
         * </p>
         * <p>
         * Returns {@code null} if {@link #isClassNameIncluded(JfrParser)} returns {@code false}
         * </p>
         */
        @Nullable
        private String resolveClassName(JfrParser parser) {
            return resolve(parser, true);
        }

        /**
         * Returns {@code true} when the class name matches the matchers provided via {@link #parse(File, List, List)}
         *
         * @param parser
         * @return
         */
        private boolean isClassNameIncluded(JfrParser parser) {
            return resolve(parser, true) != EXCLUDED;
        }

        @Nullable
        private String resolve(JfrParser parser, boolean className) {
            if (resolved == null) {
                StringBuilder stringBuilder = parser.resolveSymbol(pos, className);
                if (className && !parser.isClassIncluded(stringBuilder)) {
                    resolved = EXCLUDED;
                } else {
                    resolved = stringBuilder.toString();
                }
            }
            if (resolved.isEmpty()) {
                return null;
            }
            return resolved;
        }
    }
}
