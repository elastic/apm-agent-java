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
package co.elastic.apm.agent.vertx;

import co.elastic.apm.agent.util.IOUtils;
import io.netty.buffer.ByteBuf;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CoderResult;

public class NettyByteTransfer extends IOUtils {

    public static CoderResult decodeUtf8BytesFromTransfer(final ByteBuf src, final CharBuffer dest) {
        // to be compatible with Java 8, we have to cast to buffer because of different return types
        final ByteBuffer buffer = threadLocalByteBuffer.get();
        int readableBytes = src.readableBytes();
        CoderResult result = null;
        while (readableBytes > 0) {
            int length = Math.min(readableBytes, BYTE_BUFFER_CAPACITY);
            ((Buffer) buffer).limit(length);
            ((Buffer) buffer).position(0);
            src.readBytes(buffer);
            ((Buffer) buffer).position(0);
            result = decode(dest, buffer);
            if (result.isError() || result.isOverflow()) {
                return result;
            }
            readableBytes = src.readableBytes();
        }

        return result == null ? CoderResult.OVERFLOW : result;
    }
}
