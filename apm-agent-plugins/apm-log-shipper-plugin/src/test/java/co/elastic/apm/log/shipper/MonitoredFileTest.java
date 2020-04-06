package co.elastic.apm.log.shipper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


public class MonitoredFileTest {

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
        monitoredFile.poll(ByteBuffer.allocate(1024), logListener, 4);
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
    void testReadTwoLines() throws IOException {
        byte[] bytes = {'c', 'a', 'f', 'e', '\n', 'b', 'a', 'b', 'e', '\r', '\n'};
        MonitoredFile.readLines(new File("foo.log"), bytes, bytes.length, 2, logListener);
        assertThat(logListener.lines).hasSize(2);
        assertThat(logListener.lines.get(0)).isEqualTo(new byte[]{'c', 'a', 'f', 'e'});
        assertThat(logListener.lines.get(1)).isEqualTo(new byte[]{'b', 'a', 'b', 'e'});
    }

    private static class ListFileChangeListener implements FileChangeListener {
        private final List<byte[]> lines = new ArrayList<>();

        @Override
        public void onLineAvailable(File file, byte[] line, int offset, int length, boolean eol) throws IOException {
            lines.add(Arrays.copyOfRange(line, offset, offset + length));
        }
    }
}
