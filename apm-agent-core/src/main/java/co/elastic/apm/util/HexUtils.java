package co.elastic.apm.util;

import java.io.IOException;
import java.io.OutputStream;

public class HexUtils {

    private final static char[] hexArray = "0123456789abcdef".toCharArray();

    private HexUtils() {
        // only static utility methods, don't instantiate
    }

    /**
     * Converts a byte array to a hex encoded (aka base 16 encoded) string
     * <p>
     * From https://stackoverflow.com/a/9855338
     * </p>
     *
     * @param bytes The input byte array.
     * @return A hex encoded string representation of the byte array.
     */
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = hexArray[v >>> 4];
            hexChars[i * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static void writeBytesAsHex(byte[] bytes, OutputStream outputStream) throws IOException {
        for (byte b : bytes) {
            int v = b & 0xFF;
            outputStream.write(hexArray[v >>> 4]);
            outputStream.write(hexArray[v & 0x0F]);
        }
    }
}
