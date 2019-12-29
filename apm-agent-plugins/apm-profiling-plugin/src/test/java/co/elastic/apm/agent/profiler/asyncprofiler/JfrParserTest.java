package co.elastic.apm.agent.profiler.asyncprofiler;

import co.elastic.apm.agent.impl.transaction.StackFrame;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class JfrParserTest {

    @Test
    void name() throws Exception {
        JfrParser jfrParser = new JfrParser(new File(getClass().getClassLoader().getResource("recording.jfr").getFile()));
        assertThat(jfrParser.getMajor()).isZero();
        assertThat(jfrParser.getMinor()).isEqualTo((short) 9);
        ArrayList<StackFrame> stackFrames = new ArrayList<>();
        SortedSet<StackTraceEvent> stackTraces = new TreeSet<>();
        jfrParser.parse((threadId, stackTraceId, nanoTime) -> {
            stackTraces.add(new StackTraceEvent(nanoTime, stackTraceId, threadId));
            jfrParser.getStackTrace(stackTraceId, false, stackFrames);
            assertThat(stackFrames).isNotEmpty();
            stackFrames.clear();
        });
        assertThat(stackTraces).hasSize(572);
        for (Iterator<StackTraceEvent> iterator = stackTraces.iterator(); iterator.hasNext(); ) {
            StackTraceEvent stackTrace = iterator.next();
            iterator.remove();
            System.out.println(stackTrace.nanoTime - 71432002478457L);
        }
    }

    private static class StackTraceEvent implements Comparable<StackTraceEvent> {
        private final long nanoTime;
        private final long stackTraceId;
        private final int threadId;

        private StackTraceEvent(long nanoTime, long stackTraceId, int threadId) {
            this.nanoTime = nanoTime;
            this.stackTraceId = stackTraceId;
            this.threadId = threadId;
        }

        @Override
        public int compareTo(StackTraceEvent o) {
            return Long.compare(nanoTime, o.nanoTime);
        }
    }

}
