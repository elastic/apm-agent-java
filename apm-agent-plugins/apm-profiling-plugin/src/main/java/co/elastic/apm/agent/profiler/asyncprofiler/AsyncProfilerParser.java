package co.elastic.apm.agent.profiler.asyncprofiler;

import co.elastic.apm.agent.matcher.WildcardMatcher;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AsyncProfilerParser {

    private final RandomAccessFile file;
    private final MappedByteBuffer buffer;
    private final List<WildcardMatcher> excludedClasses;
    private final List<WildcardMatcher> includedClasses;
    private final List<String> stackTraceElements = new ArrayList<>(256);
    private final StringBuilder lineBuffer = new StringBuilder();
    private final Map<CachedString, String> stringCache = new HashMap<CachedString, String>();
    private final CachedString.WrappedStringBuilder latentKey = new CachedString.WrappedStringBuilder();

    public AsyncProfilerParser(File buffer, List<WildcardMatcher> includedClasses, List<WildcardMatcher> excludedClasses) throws IOException {
        file = new RandomAccessFile(buffer, "r");
        this.buffer = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, file.length());
        this.includedClasses = includedClasses;
        this.excludedClasses = excludedClasses;
    }


    public void parse(StackTraceCallback callback) throws IOException {
        parse(null, callback);
    }

    public void parse(@Nullable long[] threadIds, StackTraceCallback callback) throws IOException {
        try {
            readLine();
            while (readStackTrace(threadIds, callback)) ;
        } finally {
            file.close();
        }
    }

    private boolean readStackTrace(@Nullable long[] threadIds, StackTraceCallback callback) throws IOException {
        if (threadIds != null) {
            Arrays.sort(threadIds);
        }
        CharSequence header = skipLinesUntilStartsWith("---");
        if (header == null) {
            return false;
        }
        int samples = parseSamplesCount(header);
        long threadId = parseThreadId();

        // TODO how to convert from native thread ids to java thread ids?
        boolean threadIncluded = threadIds == null || Arrays.binarySearch(threadIds, threadId) >= 0;
        if (threadIncluded) {
            for (CharSequence line = readLine(); line != null && line.length() > 0; line = readLine()) {
                if (!StringUtils.contains(line, "tid=")) {
                    String javaFrame = parseJavaFrame(line);
                    if (javaFrame != null) {
                        stackTraceElements.add(javaFrame);
                    }
                } else {
                    readLine();
                    break;
                }
            }

            if (samples > 1 && threadId > -1) {
                callback.onCallTree(stackTraceElements, threadId, samples);
            }
        }

        stackTraceElements.clear();
        return true;
    }

    /**
     * Skips the stack frames, reads the thread id and resets the position to the current position
     *
     * <pre>
     * --- 150000000 ns (2.33%), 15 samples
     *   [ 0] __psynch_cvwait                     < 1: current pos; 4: reset position
     *   [ 1] os::PlatformEvent::park(long)
     *   [ 2] os::sleep(Thread*, long, bool)
     *   [ 3] JVM_Sleep
     *   [ 4] java.lang.Thread.sleep_[j]
     *   [ 5] one.profiler.AsyncProfiler.b_[j]
     *   [ 6] one.profiler.AsyncProfiler.a_[j]
     *   [ 7] one.profiler.AsyncProfiler.main_[j]
     *   [ 8] [main tid=6659]                     < 3: parse the thread id form the line above
     *                                            < 2: searching for two consecutive line breaks
     * ...
     * </pre>
     *
     * @return the thread
     * @throws IOException
     */
    private long parseThreadId() throws IOException {
        MappedByteBuffer buffer = this.buffer;
        int pos = buffer.position();
        while (buffer.hasRemaining()) {
            if (buffer.get() == '\n') {
                if (buffer.get() == '\n') {
                    try {
                        readPreviousLine();
                        CharSequence line = readPreviousLine();
                        return parseThreadId(line);
                    } finally {
                        buffer.position(pos);
                    }
                }
            }
        }
        return -1;
    }

    @Nullable
    private CharSequence readPreviousLine() throws IOException {
        MappedByteBuffer buffer = this.buffer;
        buffer.position(buffer.position() - 2);
        while (buffer.position() > 2) {
            if (buffer.get() == '\n') {
                int pos = buffer.position();
                try {
                    return readLine();
                } finally {
                    buffer.position(pos);
                }
            } else {
                buffer.position(buffer.position() - 2);
            }
        }
        return null;
    }

    @Nullable
    public CharSequence skipLinesUntilStartsWith(CharSequence startsWith) throws IOException {
        while (buffer.hasRemaining()) {
            CharSequence line = readLine();
            if (line == null) {
                return null;
            } else if (StringUtils.startsWith(line, startsWith)) {
                return line;
            }
        }
        return null;
    }

    //  [ 5] jdk.jfr.internal.PlatformRecorder.takeNap_[j]
    @Nullable
    String parseJavaFrame(CharSequence line) {
        if (StringUtils.endsWith(line, "_[j]")) {
            int end = line.length() - 4;
            int start = StringUtils.indexOf(line, "] ") + 2;
            CachedString.WrappedStringBuilder latentKey = this.latentKey;
            StringBuilder methodSignatureBuilder = latentKey.getStringBuilder();
            methodSignatureBuilder.setLength(0);
            methodSignatureBuilder.append(line, start, end);

            if (isIncluded(methodSignatureBuilder)) {
                String cached = stringCache.get(latentKey);
                if (cached == null) {
                    cached = latentKey.getString();
                    stringCache.put(new CachedString.WrappedString(cached), cached);
                }
                return cached;
            }

        }
        return null;
    }

    private boolean isIncluded(StringBuilder methodSignatureBuilder) {
        return WildcardMatcher.isAnyMatch(includedClasses, methodSignatureBuilder) && WildcardMatcher.isNoneMatch(excludedClasses, methodSignatureBuilder);
    }

    //  [10] [JFR Periodic Tasks tid=35075]
    static long parseThreadId(CharSequence line) {
        return Long.parseLong(line, StringUtils.indexOf(line, "tid=") + 4, line.length() - 1, 10);
    }

    @Nullable
    private CharSequence readLine() throws IOException {
        if (!buffer.hasRemaining()) {
            return null;
        }
        lineBuffer.setLength(0);
        FileUtils.readLine(buffer, lineBuffer);
        return lineBuffer;
    }

    // --- 560000000 ns (1.69%), 56 samples
    static int parseSamplesCount(CharSequence line) {
        return Integer.parseInt(line, StringUtils.indexOf(line, ", ") + 2, StringUtils.indexOf(line, " sample"), 10);
    }

    public static class FileUtils {
        public static void readLine(ByteBuffer file, StringBuilder sb) {
            int c;
            boolean eol = false;
            while (!eol && file.hasRemaining()) {
                switch (c = file.get()) {
                    case '\n':
                        eol = true;
                        break;
                    case '\r':
                        eol = true;
                        int cur = file.position();
                        if ((file.getChar()) != '\n') {
                            file.position(cur);
                        }
                        break;
                    default:
                        sb.append((char) c);
                        break;
                }
            }
        }
    }

    // TODO mostly copied form apache StringUtils - add to NOTICE
    public static class StringUtils {

        public static boolean contains(final CharSequence seq, final String searchSeq) {
            return indexOf(seq, searchSeq) >= 0;
        }

        private static int indexOf(CharSequence input, String s) {
            if (input instanceof StringBuilder) {
                return ((StringBuilder) input).indexOf(s);
            }
            return input.toString().indexOf(s);
        }

        public static boolean endsWith(final CharSequence str, final CharSequence suffix) {
            return endsWith(str, suffix, false);
        }

        private static boolean endsWith(final CharSequence str, final CharSequence suffix, final boolean ignoreCase) {
            if (suffix.length() > str.length()) {
                return false;
            }
            final int strOffset = str.length() - suffix.length();
            return regionMatches(str, ignoreCase, strOffset, suffix, 0, suffix.length());
        }

        public static boolean startsWith(final CharSequence str, final CharSequence prefix) {
            return startsWith(str, prefix, false);
        }

        private static boolean startsWith(final CharSequence str, final CharSequence prefix, final boolean ignoreCase) {
            if (prefix.length() > str.length()) {
                return false;
            }
            return regionMatches(str, ignoreCase, 0, prefix, 0, prefix.length());
        }

        public static boolean equals(final CharSequence cs1, final CharSequence cs2) {
            if (cs1 == cs2) {
                return true;
            }
            if (cs1.length() != cs2.length()) {
                return false;
            }
            if (cs1 instanceof String && cs2 instanceof String) {
                return cs1.equals(cs2);
            }
            return regionMatches(cs1, false, 0, cs2, 0, cs1.length());
        }

        static boolean regionMatches(final CharSequence cs, final boolean ignoreCase, final int thisStart,
                                     final CharSequence substring, final int start, final int length) {
            if (cs instanceof String && substring instanceof String) {
                return ((String) cs).regionMatches(ignoreCase, thisStart, (String) substring, start, length);
            }
            int index1 = thisStart;
            int index2 = start;
            int tmpLen = length;

            // Extract these first so we detect NPEs the same as the java.lang.String version
            final int srcLen = cs.length() - thisStart;
            final int otherLen = substring.length() - start;

            // Check for invalid parameters
            if (thisStart < 0 || start < 0 || length < 0) {
                return false;
            }

            // Check that the regions are long enough
            if (srcLen < length || otherLen < length) {
                return false;
            }

            while (tmpLen-- > 0) {
                final char c1 = cs.charAt(index1++);
                final char c2 = substring.charAt(index2++);

                if (c1 == c2) {
                    continue;
                }

                if (!ignoreCase) {
                    return false;
                }

                // The same check as in String.regionMatches():
                if (Character.toUpperCase(c1) != Character.toUpperCase(c2)
                    && Character.toLowerCase(c1) != Character.toLowerCase(c2)) {
                    return false;
                }
            }
            return true;
        }
    }

    private interface CachedString {
        String getString();
        CharSequence getCharSequence();

        class WrappedString implements CachedString {

            private final String string;

            public WrappedString(String string) {
                this.string = string;
            }

            @Override
            public String getString() {
                return string;
            }

            @Override
            public CharSequence getCharSequence() {
                return string;
            }

            @Override
            public boolean equals(Object o) {
                if (!(o instanceof CachedString)) {
                    return false;
                }
                return StringUtils.equals(string, ((CachedString) o).getCharSequence());
            }

            @Override
            public int hashCode() {
                return string.hashCode();
            }
        }

        class WrappedStringBuilder implements CachedString {

            private final StringBuilder stringBuilder;

            public WrappedStringBuilder() {
                this.stringBuilder = new StringBuilder();
            }

            @Override
            public String getString() {
                return stringBuilder.toString();
            }

            @Override
            public CharSequence getCharSequence() {
                return stringBuilder;
            }

            public StringBuilder getStringBuilder() {
                return stringBuilder;
            }

            @Override
            public boolean equals(Object o) {
                if (!(o instanceof CachedString)) {
                    return false;
                }
                return StringUtils.equals(stringBuilder, ((CachedString) o).getCharSequence());
            }

            @Override
            public int hashCode() {
                int h = 0;
                for (int i = 0; i < stringBuilder.length(); i++) {
                    h = 31 * h + stringBuilder.charAt(i);
                }
                return h;
            }
        }
    }
}
