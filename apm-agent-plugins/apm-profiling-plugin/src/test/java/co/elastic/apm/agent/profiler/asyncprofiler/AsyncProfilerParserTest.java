package co.elastic.apm.agent.profiler.asyncprofiler;

import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncProfilerParserTest {

    private AsyncProfilerParser parser;

    @BeforeEach
    void setUp() throws Exception {
        parser = new AsyncProfilerParser(new File(getClass().getResource("/trace.txt").getFile()), WildcardMatcher.matchAllList(), Collections.emptyList());
    }

    @Test
    void testParse() throws Exception {
        AtomicInteger count = new AtomicInteger();
        parser.parse((stackTraceElements, threadId, samples) -> {
            count.getAndIncrement();
            if (count.get() == 1) {
                assertThat(threadId).isEqualTo(1);
                assertThat(samples).isEqualTo(56);
                assertThat(stackTraceElements).isEmpty();
            } else if (count.get() == 2) {
                assertThat(threadId).isEqualTo(2);
                assertThat(samples).isEqualTo(56);
                assertThat(stackTraceElements).containsExactly(
                    "java.lang.ref.Reference.waitForReferencePendingList",
                    "java.lang.ref.Reference.processPendingReferences",
                    "java.lang.ref.Reference$ReferenceHandler.run");
            }
        });
        assertThat(count.get()).isEqualTo(2);
    }

    @Test
    void getSamples() {
        assertThat(AsyncProfilerParser.parseSamplesCount("--- 560000000 ns (1.69%), 56 samples")).isEqualTo(56);
        assertThat(AsyncProfilerParser.parseSamplesCount("--- 560000000 ns (1.69%), 1 sample")).isEqualTo(1);
    }

    @Test
    void getJavaFrame() {
        assertThat(parser.parseJavaFrame("  [ 5] jdk.jfr.internal.PlatformRecorder.takeNap_[j]")).isEqualTo("jdk.jfr.internal.PlatformRecorder.takeNap");
        assertThat(parser.parseJavaFrame("  [ 3] JVM_MonitorWait")).isNull();
    }

    @Test
    void parseThreadId() {
        assertThat(AsyncProfilerParser.parseThreadId("  [10] [JFR Periodic Tasks tid=35075]")).isEqualTo(35075);
    }
}
