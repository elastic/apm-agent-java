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
import org.elasticsearch.common.io.stream.InputStreamStreamInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;

@VisibleForAdvice
public class ESRestClientInstrumentationHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ESRestClientInstrumentationHelper.class);

    private static ThreadLocal<byte[]> bodyReadBuffer = new ThreadLocal<>();

    @Nullable
    @VisibleForAdvice
    public static String readRequestBody(InputStream bodyIS, long contentLength, String endpoint) throws IOException {
        String body = null;
        try {
            byte[] data = bodyReadBuffer.get();
            if (data == null) {
                // The DslJsonSerializer.MAX_LONG_STRING_VALUE_LENGTH is actually used to count chars and not bytes, but that's not
                // that important, the most important is that we limit the payload size we read and decode
                data = new byte[DslJsonSerializer.MAX_LONG_STRING_VALUE_LENGTH];
                bodyReadBuffer.set(data);
            }

            try {
                body = new InputStreamStreamInputWrapper(bodyIS, contentLength).readString();
            } catch (Exception e) {
                LOGGER.info("Failed to read request body for " + endpoint);
            }

        }
        finally {
            bodyIS.close();
        }

        return body;
    }

    private static class InputStreamStreamInputWrapper extends InputStreamStreamInput {
        private byte[] contentLengthBytes = new byte[5];
        private int contentLengthValidReadIndex = 0;
        private int contentLengthByteReadIndex;

        InputStreamStreamInputWrapper(InputStream is, long contentLength) {
            super(is);
            // Encoding the content length into bytes. This size must be returned from the InputStream before the content is returned.
            for (int i=0; i<contentLengthBytes.length; i++) {
                contentLengthValidReadIndex++;
                contentLengthBytes[i] = (byte)(contentLength & 0x7F);
                contentLength >>>= 7;
                if (contentLength == 0) {
                    break;
                }
                contentLengthBytes[i] |= 0x80;
            }
            contentLengthByteReadIndex = 0;
        }

        @Override
        public byte readByte() throws IOException {
            if(contentLengthByteReadIndex < contentLengthValidReadIndex) {
                return contentLengthBytes[contentLengthByteReadIndex++];
            }
            return super.readByte();
        }
    }
}
