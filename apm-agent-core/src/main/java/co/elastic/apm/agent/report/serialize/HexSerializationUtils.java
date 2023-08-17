package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.tracer.util.HexUtils;
import com.dslplatform.json.JsonWriter;

public class HexSerializationUtils {
    public static void writeBytesAsHex(byte[] bytes, JsonWriter jw) {
        for (int i = 0; i < bytes.length; i++) {
            writeHexByte(jw, bytes[i]);
        }
    }

    private static void writeHexByte(JsonWriter jw, byte b) {
        int v = b & 0xFF;
        jw.writeByte((byte) HexUtils.HEX_CHARS[v >>> 4]);
        jw.writeByte((byte) HexUtils.HEX_CHARS[v & 0x0F]);
    }

    public static void writeAsHex(long l, JsonWriter jw) {
        writeHexByte(jw, (byte) (l >> 56));
        writeHexByte(jw, (byte) (l >> 48));
        writeHexByte(jw, (byte) (l >> 40));
        writeHexByte(jw, (byte) (l >> 32));
        writeHexByte(jw, (byte) (l >> 24));
        writeHexByte(jw, (byte) (l >> 16));
        writeHexByte(jw, (byte) (l >> 8));
        writeHexByte(jw, (byte) l);
    }
}
