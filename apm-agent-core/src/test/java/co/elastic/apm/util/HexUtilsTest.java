package co.elastic.apm.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class HexUtilsTest {

    @Test
    void bytesToHex() throws IOException {
        byte[] bytes = new byte[16];
        new Random().nextBytes(bytes);
        ByteArrayOutputStream os = new ByteArrayOutputStream(bytes.length * 2);
        HexUtils.writeBytesAsHex(bytes, os);
        String hexAsString = new String(os.toByteArray());
        assertThat(hexAsString).isEqualTo(HexUtils.bytesToHex(bytes));
    }
}
