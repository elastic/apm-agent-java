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
package co.elastic.apm.benchmark.serializer;

import co.elastic.apm.impl.payload.Payload;
import co.elastic.apm.report.serialize.PayloadSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import okio.BufferedSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class JacksonPayloadSerializer implements PayloadSerializer {
    private static final Logger logger = LoggerFactory.getLogger(JacksonPayloadSerializer.class);
    private final ObjectMapper objectMapper;

    public JacksonPayloadSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void serializePayload(BufferedSink sink, Payload payload) throws IOException {
        objectMapper.writeValue(sink.outputStream(), payload);
        if (logger.isTraceEnabled()) {
            logger.trace(objectMapper.writeValueAsString(payload));
        }
    }
}
