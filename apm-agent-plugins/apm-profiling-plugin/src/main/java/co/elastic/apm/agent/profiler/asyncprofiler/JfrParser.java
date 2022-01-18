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
package co.elastic.apm.agent.profiler.asyncprofiler;

import co.elastic.apm.agent.impl.transaction.StackFrame;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.objectpool.Recyclable;
import co.elastic.apm.agent.profiler.collections.Int2IntHashMap;
import co.elastic.apm.agent.profiler.collections.Int2ObjectHashMap;
import co.elastic.apm.agent.profiler.collections.Long2LongHashMap;
import co.elastic.apm.agent.profiler.collections.Long2ObjectHashMap;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

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
    private static final int BIG_FILE_BUFFER_SIZE = 5 * 1024 * 1024;
    private static final int SMALL_FILE_BUFFER_SIZE = 4 * 1024;
    private static final String SYMBOL_EXCLUDED = "3x cluded";
    private static final String SYMBOL_NULL = "n u11";
    private final static StackFrame FRAME_EXCLUDED = new StackFrame("excluded", "excluded");
    private final static StackFrame FRAME_NULL = new StackFrame("null", "null");

    private final BufferedFile bufferedFile;
    private final Int2IntHashMap classIdToClassNameSymbolId = new Int2IntHashMap(-1);
    private final Int2IntHashMap symbolIdToPos = new Int2IntHashMap(-1);
    private final Int2ObjectHashMap<String> symbolIdToString = new Int2ObjectHashMap<String>();
    private final Int2IntHashMap stackTraceIdToFilePositions = new Int2IntHashMap(-1);
    private final Long2LongHashMap nativeTidToJavaTid = new Long2LongHashMap(-1);
    private final Long2ObjectHashMap<StackFrame> frameIdToFrame = new Long2ObjectHashMap<StackFrame>();
    private final Long2LongHashMap frameIdToMethodSymbol = new Long2LongHashMap(-1);
    private final Long2LongHashMap frameIdToClassId = new Long2LongHashMap(-1);
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
        this(ByteBuffer.allocateDirect(BIG_FILE_BUFFER_SIZE), ByteBuffer.allocateDirect(SMALL_FILE_BUFFER_SIZE));
    }

    JfrParser(ByteBuffer bigBuffer, ByteBuffer smallBuffer) {
        bufferedFile = new BufferedFile(bigBuffer, smallBuffer);
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
        if (fileSize < 16) {
            throw new IllegalStateException("Unexpected sampling profiler error, everything else should work as expected. " +
                "Please report to us with as many details, including OS and JVM details.");
        }
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
                for (int i = 0; i < count; i++) {
                    int threadId = bufferedFile.getInt();
                    String threadName = readUtf8String().toString();
                }
                break;
            case ContentTypeId.CONTENT_JAVA_THREAD:
                for (int i = 0; i < count; i++) {
                    bufferedFile.ensureRemaining(16);
                    long javaThreadId = bufferedFile.getUnsafeLong();
                    int nativeThreadId = bufferedFile.getUnsafeInt();
                    int threadGroup = bufferedFile.getUnsafeInt();
                    nativeTidToJavaTid.put(nativeThreadId, javaThreadId);
                }
                break;
            case ContentTypeId.CONTENT_THREAD_GROUP:
                // no info
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
                    frameIdToFrame.put(id, FRAME_NULL);
                    frameIdToClassId.put(id, classId);
                    frameIdToMethodSymbol.put(id, methodNameSymbolId);
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
                    symbolIdToPos.put(symbolId, pos);
                    symbolIdToString.put(symbolId, SYMBOL_NULL);
                    skipString();
                }
                break;
            case ContentTypeId.CONTENT_STATE:
                // we're not really interested in the thread states
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
            long javaThreadId = nativeTidToJavaTid.get(tid);
            if (javaThreadId != -1) {
                callback.onCallTree(javaThreadId, stackTraceId, nanoTime);
            }
        }
    }

    /**
     * Resolves the stack trace with the given {@code stackTraceId}.
     * <p>
     * Note that his allocates strings for symbols in case a stack frame has not already been resolved for the current JFR file yet.
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
     * @throws IOException if there is an error reading in current buffer
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
            if (stackFrames.size() > maxStackDepth) {
                stackFrames.remove(0);
            }
        }
        bufferedFile.position(position);
    }

    private void addFrameIfIncluded(List<StackFrame> stackFrames, boolean onlyJavaFrames, long frameId, byte frameType) throws IOException {
        if (!onlyJavaFrames || isJavaFrameType(frameType)) {
            StackFrame stackFrame = resolveStackFrame(frameId);
            if (stackFrame != FRAME_EXCLUDED) {
                stackFrames.add(stackFrame);
            }
        }
    }

    private boolean isJavaFrameType(byte frameType) {
        return isJavaFrameType[frameType];
    }

    private String resolveSymbol(int id, boolean classSymbol) throws IOException {
        String symbol = symbolIdToString.get(id);
        if (symbol != SYMBOL_NULL) {
            return symbol;
        }
        StringBuilder symbolBuilder = resolveSymbolBuilder(symbolIdToPos.get(id), classSymbol);
        if (classSymbol && !isClassIncluded(symbolBuilder)) {
            symbol = SYMBOL_EXCLUDED;
        } else {
            symbol = symbolBuilder.toString();
        }
        symbolIdToString.put(id, symbol);
        return symbol;
    }

    private StringBuilder resolveSymbolBuilder(int pos, boolean replaceSlashWithDot) throws IOException {
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

    private StackFrame resolveStackFrame(long frameId) throws IOException {
        StackFrame stackFrame = frameIdToFrame.get(frameId);
        if (stackFrame != FRAME_NULL) {
            return stackFrame;
        }
        String className = resolveSymbol(classIdToClassNameSymbolId.get((int) frameIdToClassId.get(frameId)), true);
        if (className == SYMBOL_EXCLUDED) {
            stackFrame = FRAME_EXCLUDED;
        } else {
            String method = resolveSymbol((int) frameIdToMethodSymbol.get(frameId), false);
            stackFrame = new StackFrame(className, Objects.requireNonNull(method));
        }
        frameIdToFrame.put(frameId, stackFrame);
        return stackFrame;
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
        stackTraceIdToFilePositions.clear();
        frameIdToFrame.clear();
        frameIdToMethodSymbol.clear();
        frameIdToClassId.clear();
        symbolBuilder.setLength(0);
        excludedClasses = null;
        includedClasses = null;
        symbolIdToPos.clear();
        symbolIdToString.clear();
    }

    public interface StackTraceConsumer {

        /**
         * @param threadId     The {@linkplain Thread#getId() Java thread id} for with the event was recorded.
         * @param stackTraceId The id of the stack trace event.
         *                     Can be used to resolve the stack trace via {@link #resolveStackTrace(long, boolean, List, int)}
         * @param nanoTime     The timestamp of the event which can be correlated with {@link System#nanoTime()}
         * @throws IOException if there is any error reading stack trace
         */
        void onCallTree(long threadId, long stackTraceId, long nanoTime) throws IOException;
    }

    private interface EventTypeId {
        int EVENT_METADATA           = 0;
        int EVENT_CHECKPOINT         = 1;
        int EVENT_RECORDING          = 10;
        int EVENT_EXECUTION_SAMPLE   = 20;
    }

    private interface ContentTypeId {
        int CONTENT_THREAD       = 7;
        int CONTENT_JAVA_THREAD  = 8;
        int CONTENT_STACKTRACE   = 9;
        int CONTENT_CLASS        = 10;
        int CONTENT_THREAD_GROUP = 31;
        int CONTENT_METHOD       = 32;
        int CONTENT_SYMBOL       = 33;
        int CONTENT_STATE        = 34;
        int CONTENT_FRAME_TYPE   = 47;
    }
}
