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
package co.elastic.apm.es.restclient;

import co.elastic.apm.bci.VisibleForAdvice;
import co.elastic.apm.report.serialize.DslJsonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;

@VisibleForAdvice
public class ESRestClientInstrumentationHelper {
    private static final Logger logger = LoggerFactory.getLogger(ESRestClientInstrumentationHelper.class);
    private static final byte CURLY_BRACKET_UTF8 = '{';
    private static ThreadLocal<byte[]> bodyReadBuffer = new ThreadLocal<>();
    private static ThreadLocal<ByteBuffer> bodyBuffer = new ThreadLocal<>() {
        @Override
        protected ByteBuffer initialValue() {
            return ByteBuffer.allocate(1024);
        }
    };
    private static ThreadLocal<CharsetDecoder> threadLocalCharsetDecoder = new ThreadLocal<>() {
        @Override
        protected CharsetDecoder initialValue() {
            return StandardCharsets.UTF_8.newDecoder();
        }
    };

    @Nullable
    @VisibleForAdvice
    public static String readRequestBody(InputStream bodyIS, String endpoint) throws IOException {
        String body = null;
        try {
            byte[] data = bodyReadBuffer.get();
            if (data == null) {
                // The DslJsonSerializer.MAX_LONG_STRING_VALUE_LENGTH is actually used to count chars and not bytes, but that's not
                // that important, the most important is that we limit the payload size we read and decode
                data = new byte[DslJsonSerializer.MAX_LONG_STRING_VALUE_LENGTH];
                bodyReadBuffer.set(data);
            }
            int length = bodyIS.read(data, 0, data.length);

            // read only if UTF8-encoded (based on the first byte being UTF8 of curly bracket char)
            if (data[0] == CURLY_BRACKET_UTF8) {
                body = new String(data, 0, length, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            logger.info("Failed to read request body for " + endpoint);
        } finally {
            bodyIS.close();
        }

        return body;
    }

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
    public static boolean readUtf8Stream(InputStream is, CharBuffer charBuffer) throws IOException {
        final ByteBuffer buffer = bodyBuffer.get();
        final CharsetDecoder charsetDecoder = threadLocalCharsetDecoder.get();
        try (is) {
            final byte[] bufferArray = buffer.array();
            for (int read = is.read(bufferArray); read != -1; read = is.read(bufferArray)) {
                buffer.limit(read);
                final CoderResult coderResult = charsetDecoder.decode(buffer, charBuffer, true);
                buffer.clear();
                if (coderResult.isError()) {
                    return false;
                } else if (coderResult.isOverflow()) {
                    return true;
                }
            }
            return true;
        } finally {
            charsetDecoder.flush(charBuffer);
            charBuffer.flip();
            buffer.clear();
            charsetDecoder.reset();
        }
    }
}
