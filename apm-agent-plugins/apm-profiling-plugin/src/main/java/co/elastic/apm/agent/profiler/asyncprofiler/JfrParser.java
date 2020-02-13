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
import java.nio.ByteBuffer;
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
 */
public class JfrParser implements Recyclable {

    private static final Logger logger = LoggerFactory.getLogger(JfrParser.class);

    private static final byte[] MAGIC_BYTES = new byte[]{'F', 'L', 'R', '\0'};
    private static final Set<String> JAVA_FRAME_TYPES = new HashSet<>(Arrays.asList("Interpreted", "JIT compiled", "Inlined"));
    private static final int FILE_BUFFER_SIZE = 4 * 1024 * 1024;

    private final BufferedFile bufferedFile;
    private final Int2IntHashMap classIdToClassNameSymbolId = new Int2IntHashMap(-1);
    private final Int2ObjectHashMap<Symbol> symbols = new Int2ObjectHashMap<>();
    private final Int2IntHashMap stackTraceIdToFilePositions = new Int2IntHashMap(-1);
    private final Long2ObjectHashMap<LazyStackFrame> framesByFrameId = new Long2ObjectHashMap<>();
    // used to resolve a symbol with minimal allocations
    private final StringBuilder symbolBuilder = new StringBuilder();
    private long eventsOffset;
    private long metadataOffset;
    @Nullable
    private boolean[] isJavaFrameType;
    @Nullable
    private List<WildcardMatcher> excludedClasses;
    @Nullable
    private List<WildcardMatcher> includedClasses;

    public JfrParser() {
        this(ByteBuffer.allocateDirect(FILE_BUFFER_SIZE));
    }

    JfrParser(ByteBuffer buffer) {
        bufferedFile = new BufferedFile(buffer);
    }

    /**
     * Initializes the parser to make it ready for {@link #resolveStackTrace(long, boolean, List, int)} to be called.
     *
     * @param file            the JFR file to parse
     * @param excludedClasses Class names to exclude in stack traces (has an effect on {@link #resolveStackTrace(long, boolean, List, int)})
     * @param includedClasses Class names to include in stack traces (has an effect on {@link #resolveStackTrace(long, boolean, List, int)})
     * @throws IOException if some I/O error occurs
     */
    public void parse(File file, List<WildcardMatcher> excludedClasses, List<WildcardMatcher> includedClasses) throws IOException {
        this.excludedClasses = excludedClasses;
        this.includedClasses = includedClasses;
        bufferedFile.setFile(file);
        long fileSize = bufferedFile.size();
        logger.debug("Parsing {} ({} bytes)", file, fileSize);
        bufferedFile.ensureRemaining(16, 16);
        for (byte magicByte : MAGIC_BYTES) {
            if (bufferedFile.get() != magicByte) {
                throw new IllegalArgumentException("Not a JFR file");
            }
        }
        short major = bufferedFile.getShort();
        short minor = bufferedFile.getShort();
        if (major != 0 || minor != 9) {
            throw new IllegalArgumentException(String.format("Can only parse version 0.9. Was %d.%d", major, minor));
        }
        metadataOffset = bufferedFile.getLong();
        eventsOffset = bufferedFile.position();

        long checkpointOffset = parseMetadata(metadataOffset);
        parseCheckpoint(checkpointOffset);
    }

    private long parseMetadata(long metadataOffset) throws IOException {
        bufferedFile.position(metadataOffset);
        bufferedFile.ensureRemaining(8, 8);
        int size = bufferedFile.getInt();
        expectEventType(EventTypeId.EVENT_METADATA);
        bufferedFile.skip(size - 16);
        bufferedFile.ensureRemaining(8, 8);
        return bufferedFile.getLong();
    }

    private void expectEventType(int expectedEventType) throws IOException {
        int eventType = bufferedFile.getInt();
        if (eventType != expectedEventType) {
            throw new IOException("Expected " + expectedEventType + " but got " + eventType);
        }
    }

    private void parseCheckpoint(long checkpointOffset) throws IOException {
        bufferedFile.position(checkpointOffset);
        int size = bufferedFile.getInt();// size
        expectEventType(EventTypeId.EVENT_CHECKPOINT);
        bufferedFile.getLong(); // stop timestamp
        bufferedFile.getLong(); // previous checkpoint - always 0 in async-profiler
        while (bufferedFile.position() < metadataOffset) {
            parseContent();
        }
    }

    private void parseContent() throws IOException {
        BufferedFile bufferedFile = this.bufferedFile;
        int contentTypeId = bufferedFile.getInt();
        logger.debug("Parsing content type {}", contentTypeId);
        int count = bufferedFile.getInt();
        switch (contentTypeId) {
            case ContentTypeId.CONTENT_THREAD:
                // currently no thread info
                break;
            case ContentTypeId.CONTENT_STACKTRACE:
                for (int i = 0; i < count; i++) {
                    bufferedFile.ensureRemaining(13);
                    int pos = (int) bufferedFile.position();
                    // always an integer
                    // see profiler.h
                    // MAX_CALLTRACES = 65536
                    int stackTraceKey = (int) bufferedFile.getUnsafeLong();
                    this.stackTraceIdToFilePositions.put(stackTraceKey, pos);
                    bufferedFile.getUnsafe(); // truncated
                    int numFrames = bufferedFile.getUnsafeInt();
                    int sizeOfFrame = 13;
                    bufferedFile.skip(numFrames * sizeOfFrame);
                }
                break;
            case ContentTypeId.CONTENT_CLASS:
                for (int i = 0; i < count; i++) {
                    bufferedFile.ensureRemaining(26);
                    // classId is an incrementing integer, no way there are more than 2 billion distinct ones
                    int classId = (int) bufferedFile.getUnsafeLong();
                    bufferedFile.getUnsafeLong(); // loader class
                    // symbol ids are incrementing integers, no way there are more than 2 billion distinct ones
                    int classNameSymbolId = (int) bufferedFile.getUnsafeLong();
                    classIdToClassNameSymbolId.put(classId, classNameSymbolId); // class name
                    bufferedFile.getUnsafeShort(); // access flags
                }
                break;
            case ContentTypeId.CONTENT_METHOD:
                for (int i = 1; i <= count; i++) {
                    bufferedFile.ensureRemaining(35);
                    long id = bufferedFile.getUnsafeLong();
                    // classId is an incrementing integer, no way there are more than 2 billion distinct ones
                    int classId = (int) bufferedFile.getUnsafeLong();
                    // symbol ids are incrementing integers, no way there are more than 2 billion distinct ones
                    int methodNameSymbolId = (int) bufferedFile.getUnsafeLong();
                    framesByFrameId.put(id, new LazyStackFrame(classId, methodNameSymbolId));
                    bufferedFile.getUnsafeLong(); // signature
                    bufferedFile.getUnsafeShort(); // modifiers
                    bufferedFile.getUnsafe(); // hidden
                }
                break;
            case ContentTypeId.CONTENT_SYMBOL:
                for (int i = 0; i < count; i++) {
                    // symbol ids are incrementing integers, no way there are more than 2 billion distinct ones
                    int symbolId = (int) bufferedFile.getLong();
                    int pos = (int) bufferedFile.position();
                    symbols.put(symbolId, new Symbol(pos));
                    skipString();
                }
                break;
            case ContentTypeId.CONTENT_STATE:
                // we're not really interested in the thread states (async-profiler hard-codes state RUNNABLE) anyways
                // but we sill have to consume the bytes
                for (int i = 1; i <= count; i++) {
                    bufferedFile.getShort();
                    skipString();
                }
                break;
            case ContentTypeId.CONTENT_FRAME_TYPE:
                isJavaFrameType = new boolean[count + 1];
                for (int i = 1; i <= count; i++) {
                    int id = bufferedFile.get();
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

    private void skipString() throws IOException {
        int stringLength = bufferedFile.getUnsignedShort();
        bufferedFile.skip(stringLength);
    }

    /**
     * Invokes the callback for each stack trace event in the JFR file.
     *
     * @param callback called for each stack trace event
     * @throws IOException if some I/O error occurs
     */
    public void consumeStackTraces(StackTraceConsumer callback) throws IOException {
        if (!bufferedFile.isSet()) {
            throw new IllegalStateException("consumeStackTraces was called before parse");
        }
        bufferedFile.position(eventsOffset);
        while (bufferedFile.position() < metadataOffset) {
            bufferedFile.ensureRemaining(30);
            int size = bufferedFile.getUnsafeInt();
            int eventType = bufferedFile.getUnsafeInt();
            if (eventType == EventTypeId.EVENT_RECORDING) {
                return;
            }
            if (eventType != EventTypeId.EVENT_EXECUTION_SAMPLE) {
                throw new IOException("Expected " + EventTypeId.EVENT_EXECUTION_SAMPLE + " but got " + eventType);
            }
            long nanoTime = bufferedFile.getUnsafeLong();
            int tid = bufferedFile.getUnsafeInt();
            long stackTraceId = bufferedFile.getUnsafeLong();
            short threadState = bufferedFile.getUnsafeShort();
            callback.onCallTree(tid, stackTraceId, nanoTime);
        }
    }

    /**
     * Resolves the stack trace with the given {@code stackTraceId}.
     * <p>
     * Note that his allocates strings for {@link Symbol#resolved} in case a stack frame has not already been resolved for the current JFR file yet.
     * These strings are currently not cached so this can create some GC pressure.
     * </p>
     * <p>
     * Excludes frames based on the {@link WildcardMatcher}s supplied to {@link #parse(File, List, List)}.
     * </p>
     *
     * @param stackTraceId   The id of the stack traced.
     *                       Used to look up the position of the file in which the given stack trace is stored via {@link #stackTraceIdToFilePositions}.
     * @param onlyJavaFrames If {@code true}, will only resolve {@code Interpreted}, {@code JIT compiled} and {@code Inlined} frames.
     *                       If {@code false}, will also resolve {@code Native}, {@code Kernel} and {@code C++} frames.
     * @param stackFrames    The mutable list where the stack frames are written to.
     *                       Don't forget to {@link List#clear()} the list before calling this method if the list is reused.
     * @param maxStackDepth  The max size of the stackFrames list (excluded frames don't take up space).
     *                       In contrast to async-profiler's {@code jstackdepth} argument this does not truncate the bottom of the stack, only the top.
     *                       This is important to properly create a call tree without making it overly complex.
     */
    public void resolveStackTrace(long stackTraceId, boolean onlyJavaFrames, List<StackFrame> stackFrames, int maxStackDepth) throws IOException {
        if (!bufferedFile.isSet()) {
            throw new IllegalStateException("getStackTrace was called before parse");
        }
        long position = bufferedFile.position();
        bufferedFile.position(stackTraceIdToFilePositions.get((int) stackTraceId));
        bufferedFile.ensureRemaining(13);
        long stackTraceIdFromFile = bufferedFile.getUnsafeLong();
        assert stackTraceId == stackTraceIdFromFile;
        bufferedFile.getUnsafe(); // truncated
        int numFrames = bufferedFile.getUnsafeInt();
        for (int i = 0; i < numFrames; i++) {
            bufferedFile.ensureRemaining(13);
            long frameId = bufferedFile.getUnsafeLong();
            bufferedFile.getUnsafeInt(); // bci (always set to 0 by async-profiler)
            byte frameType = bufferedFile.getUnsafe();
            addFrameIfIncluded(stackFrames, onlyJavaFrames, frameId, frameType);
            if (stackFrames.size() == maxStackDepth) {
                break;
            }
        }
        bufferedFile.position(position);
    }

    private void addFrameIfIncluded(List<StackFrame> stackFrames, boolean onlyJavaFrames, long frameId, byte frameType) throws IOException {
        if (!onlyJavaFrames || isJavaFrameType(frameType)) {
            LazyStackFrame lazyStackFrame = framesByFrameId.get(frameId);
            if (lazyStackFrame.isIncluded(this)) {
                StackFrame stackFrame = lazyStackFrame.resolve(this);
                stackFrames.add(stackFrame);
            }
        }
    }

    private boolean isJavaFrameType(byte frameType) {
        return isJavaFrameType[frameType];
    }

    private StringBuilder resolveSymbol(int pos, boolean replaceSlashWithDot) throws IOException {
        long currentPos = bufferedFile.position();
        bufferedFile.position(pos);
        try {
            return readUtf8String(replaceSlashWithDot);
        } finally {
            bufferedFile.position(currentPos);
        }
    }

    private boolean isClassIncluded(CharSequence className) {
        return WildcardMatcher.isAnyMatch(includedClasses, className) && WildcardMatcher.isNoneMatch(excludedClasses, className);
    }

    private StackFrame resolveStackFrame(int classId, int methodName) throws IOException {
        Symbol classNameSymbol = symbols.get(classIdToClassNameSymbolId.get(classId));
        if (classNameSymbol.isClassNameIncluded(this)) {
            String className = classNameSymbol.resolveClassName(this);
            String method = symbols.get(methodName).resolve(this);
            return new StackFrame(className, Objects.requireNonNull(method));
        } else {
            return LazyStackFrame.EXCLUDED;
        }
    }

    private StringBuilder readUtf8String() throws IOException {
        return readUtf8String(false);
    }

    private StringBuilder readUtf8String(boolean replaceSlashWithDot) throws IOException {
        int size = bufferedFile.getUnsignedShort();
        bufferedFile.ensureRemaining(size);
        StringBuilder symbolBuilder = this.symbolBuilder;
        symbolBuilder.setLength(0);
        for (int i = 0; i < size; i++) {
            char c = (char) bufferedFile.getUnsafe();
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
        bufferedFile.resetState();
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

    public interface StackTraceConsumer {

        /**
         * @param threadId     The native thread id for with the event was recorded.
         *                     Note that this is not the same as {@link Thread#getId()}.
         * @param stackTraceId The id of the stack trace event.
         *                     Can be used to resolve the stack trace via {@link #resolveStackTrace(long, boolean, List, int)}
         * @param nanoTime     The timestamp of the event which can be correlated with {@link System#nanoTime()}
         */
        void onCallTree(int threadId, long stackTraceId, long nanoTime) throws IOException;
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

        public StackFrame resolve(JfrParser parser) throws IOException {
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
        public boolean isIncluded(JfrParser parser) throws IOException {
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
        public String resolve(JfrParser parser) throws IOException {
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
        private String resolveClassName(JfrParser parser) throws IOException {
            return resolve(parser, true);
        }

        /**
         * Returns {@code true} when the class name matches the matchers provided via {@link #parse(File, List, List)}
         *
         * @param parser
         * @return
         */
        private boolean isClassNameIncluded(JfrParser parser) throws IOException {
            return resolve(parser, true) != EXCLUDED;
        }

        @Nullable
        private String resolve(JfrParser parser, boolean className) throws IOException {
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
