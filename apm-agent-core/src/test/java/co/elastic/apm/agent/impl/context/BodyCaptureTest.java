package co.elastic.apm.agent.impl.context;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BodyCaptureTest {

    @Test
    public void testAppendTruncation() {
        BodyCaptureImpl capture = new BodyCaptureImpl();
        capture.markEligibleForCapturing();
        capture.startCapture("foobar", 10);
        assertThat(capture.isFull()).isFalse();

        capture.append("123Hello World!".getBytes(StandardCharsets.UTF_8), 3, 5);
        assertThat(capture.isFull()).isFalse();

        capture.append(" from the other side".getBytes(StandardCharsets.UTF_8), 0, 20);
        assertThat(capture.isFull()).isTrue();

        ByteBuffer content = capture.getBody();
        int size = content.position();
        byte[] contentBytes = new byte[size];
        content.position(0);
        content.get(contentBytes);

        assertThat(contentBytes).isEqualTo("Hello from".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testLifecycle() {
        BodyCaptureImpl capture = new BodyCaptureImpl();

        assertThat(capture.isEligibleForCapturing()).isFalse();
        assertThat(capture.startCapture("foobar", 42))
            .isFalse();
        assertThatThrownBy(() -> capture.append((byte) 42)).isInstanceOf(IllegalStateException.class);

        capture.markEligibleForCapturing();
        assertThat(capture.isEligibleForCapturing()).isTrue();
        assertThatThrownBy(() -> capture.append((byte) 42)).isInstanceOf(IllegalStateException.class);

        assertThat(capture.startCapture("foobar", 42))
            .isTrue();
        capture.append((byte) 42); //ensure no exception thrown

        // startCapture should return true only once
        assertThat(capture.startCapture("foobar", 42))
            .isFalse();
    }
}
