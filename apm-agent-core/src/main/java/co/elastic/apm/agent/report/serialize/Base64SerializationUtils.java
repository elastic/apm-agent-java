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

import com.dslplatform.json.JsonWriter;

public class Base64SerializationUtils {

    private static final byte[] BASE64_URL_CHARS = new byte[]{
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
        'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
        'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',
        'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
        'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
        'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
        'w', 'x', 'y', 'z', '0', '1', '2', '3',
        '4', '5', '6', '7', '8', '9', '-', '_',
    };


    public static void writeBytesAsBase64UrlSafe(byte[] data, JsonWriter jw) {
        int i = 0;
        for (; i + 2 < data.length; i += 3) {
            int b0 = ((int) data[i]) & 0xFF;
            int b1 = ((int) data[i + 1]) & 0xFF;
            int b2 = ((int) data[i + 2]) & 0xFF;
            jw.writeByte(BASE64_URL_CHARS[b0 >> 2]);
            jw.writeByte(BASE64_URL_CHARS[((b0 << 4) & 63) | (b1 >> 4)]);
            jw.writeByte(BASE64_URL_CHARS[((b1 << 2) & 63) | (b2 >> 6)]);
            jw.writeByte(BASE64_URL_CHARS[b2 & 63]);
        }
        int leftOver = data.length - i;
        if (leftOver == 1) {
            int b0 = ((int) data[i]) & 0xFF;
            jw.writeByte(BASE64_URL_CHARS[b0 >> 2]);
            jw.writeByte(BASE64_URL_CHARS[(b0 << 4) & 63]);
        } else if (leftOver == 2) {
            int b0 = ((int) data[i]) & 0xFF;
            int b1 = ((int) data[i + 1]) & 0xFF;
            jw.writeByte(BASE64_URL_CHARS[b0 >> 2]);
            jw.writeByte(BASE64_URL_CHARS[((b0 << 4) & 63) | (b1 >> 4)]);
            jw.writeByte(BASE64_URL_CHARS[(b1 << 2) & 63]);
        }
    }
}
