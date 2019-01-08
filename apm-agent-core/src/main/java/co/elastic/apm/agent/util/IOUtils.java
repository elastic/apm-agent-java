/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
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
package co.elastic.apm.agent.util;

import co.elastic.apm.agent.bci.VisibleForAdvice;

import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;

@VisibleForAdvice
public class IOUtils {
    private static ThreadLocal<ByteBuffer> threadLocalByteBuffer = new ThreadLocal<ByteBuffer>() {
        @Override
        protected ByteBuffer initialValue() {
            return ByteBuffer.allocate(1024);
        }
    };
    private static ThreadLocal<CharsetDecoder> threadLocalCharsetDecoder = new ThreadLocal<CharsetDecoder>() {
        @Override
        protected CharsetDecoder initialValue() {
            return StandardCharsets.UTF_8.newDecoder();
        }
    };

    /**
     * Reads the provided {@link InputStream} into the {@link CharBuffer} without causing allocations.
     * <p>
     * The {@link InputStream} is assumed to yield an UTF-8 encoded string.
     * </p>
     * <p>
     * If the {@link InputStream} yields more chars than the {@link CharBuffer#limit()} of the provided {@link CharBuffer},
     * the rest of the input is silently ignored.
     * </p>
     *
     * @param is         the source {@link InputStream}, which should be encoded with UTF-8.
     * @param charBuffer the {@link CharBuffer} the {@link InputStream} should be written into
     * @return {@code true}, if the input stream could be decoded with the UTF-8 charset, {@code false} otherwise.
     * @throws IOException in case of errors reading from the provided {@link InputStream}
     */
    @VisibleForAdvice
    public static boolean readUtf8Stream(final InputStream is, final CharBuffer charBuffer) throws IOException {
        // to be compatible with Java 8, we have to cast to buffer because of different return types
        final ByteBuffer buffer = threadLocalByteBuffer.get();
        final CharsetDecoder charsetDecoder = threadLocalCharsetDecoder.get();
        try {
            final byte[] bufferArray = buffer.array();
            for (int read = is.read(bufferArray); read != -1; read = is.read(bufferArray)) {
                ((Buffer) buffer).limit(read);
                final CoderResult coderResult = charsetDecoder.decode(buffer, charBuffer, true);
                ((Buffer) buffer).clear();
                if (coderResult.isError()) {
                    // this is not UTF-8
                    ((Buffer) charBuffer).clear();
                    return false;
                } else if (coderResult.isOverflow()) {
                    // stream yields more chars than the charBuffer can hold
                    break;
                }
            }
            charsetDecoder.flush(charBuffer);
            return true;
        } finally {
            ((Buffer) charBuffer).flip();
            ((Buffer) buffer).clear();
            charsetDecoder.reset();
            is.close();
        }
    }
}
