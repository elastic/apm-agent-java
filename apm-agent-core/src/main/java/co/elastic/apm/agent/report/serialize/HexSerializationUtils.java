/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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
