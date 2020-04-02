package co.elastic.apm.agent.util;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;


public class ByteUtilsTest {

    @Test
    public void putLong() {
        byte[] array = new byte[8];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        ByteUtils.putLong(array, 0, 42);
        assertThat(buffer.getLong()).isEqualTo(42);
    }

    @Test
    public void getLong() {
        byte[] array = new byte[8];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putLong(42);
        assertThat(ByteUtils.getLong(array, 0)).isEqualTo(42);
    }
}
