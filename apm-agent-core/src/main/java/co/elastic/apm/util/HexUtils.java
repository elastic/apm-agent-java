/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.util;

import com.dslplatform.json.JsonWriter;

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
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        writeBytesAsHex(bytes, sb);
        return sb.toString();
    }

    public static void writeBytesAsHex(byte[] bytes, JsonWriter jw) {
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            jw.writeByte((byte) hexArray[v >>> 4]);
            jw.writeByte((byte) hexArray[v & 0x0F]);
        }
    }

    public static void writeBytesAsHex(byte[] bytes, StringBuilder sb) {
        for (int i = 0; i < bytes.length; i++) {
            writeByteAsHex(bytes[i], sb);
        }
    }

    public static void writeByteAsHex(byte b, StringBuilder sb) {
        int v = b & 0xFF;
        sb.append(hexArray[v >>> 4]);
        sb.append(hexArray[v & 0x0F]);
    }

    public static void writeLongAsHex(long l, JsonWriter jw) {
        for (int i = 7; i >= 0; i--) {
            int v = (int) l & 0xFF;
            jw.writeByte((byte) hexArray[v >>> 4]);
            jw.writeByte((byte) hexArray[v & 0x0F]);
            l >>= 8;
        }
    }

    public static byte getNextByte(String hexEncodedString, int offset) {
        return (byte) ((Character.digit(hexEncodedString.charAt(offset), 16) << 4)
            + Character.digit(hexEncodedString.charAt(offset + 1), 16));
    }

    public static void nextBytes(String hexEncodedString, int offset, byte[] bytes) {
        for (int i = 0; i < bytes.length * 2; i += 2) {
            bytes[i / 2] = getNextByte(hexEncodedString, offset + i);
        }
    }
}
