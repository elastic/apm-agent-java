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
package co.elastic.apm.agent.util;

public class ByteUtils {
    public static void putLong(byte[] buffer, int offset, long l) {
        buffer[offset++] = (byte) (l >> 56);
        buffer[offset++] = (byte) (l >> 48);
        buffer[offset++] = (byte) (l >> 40);
        buffer[offset++] = (byte) (l >> 32);
        buffer[offset++] = (byte) (l >> 24);
        buffer[offset++] = (byte) (l >> 16);
        buffer[offset++] = (byte) (l >> 8);
        buffer[offset] = (byte) l;
    }

    public static long getLong(byte[] buffer, int offset) {
        return ((long) buffer[offset] << 56)
            | ((long) buffer[offset + 1] & 0xff) << 48
            | ((long) buffer[offset + 2] & 0xff) << 40
            | ((long) buffer[offset + 3] & 0xff) << 32
            | ((long) buffer[offset + 4] & 0xff) << 24
            | ((long) buffer[offset + 5] & 0xff) << 16
            | ((long) buffer[offset + 6] & 0xff) << 8
            | ((long) buffer[offset + 7] & 0xff);
    }
}
