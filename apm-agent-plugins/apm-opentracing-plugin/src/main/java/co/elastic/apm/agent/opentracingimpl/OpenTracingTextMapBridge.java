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
package co.elastic.apm.agent.opentracingimpl;

import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;

import javax.annotation.Nullable;
import java.util.Map;

public class OpenTracingTextMapBridge implements TextHeaderGetter<Iterable<Map.Entry<String, String>>>, TextHeaderSetter<Map<String, String>> {

    private static final OpenTracingTextMapBridge INSTANCE = new OpenTracingTextMapBridge();

    public static OpenTracingTextMapBridge instance() {
        return INSTANCE;
    }

    private OpenTracingTextMapBridge() {
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, Iterable<Map.Entry<String, String>> textMap) {
        for (Map.Entry<String, String> entry : textMap) {
            if (entry.getKey().equalsIgnoreCase(headerName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public <S> void forEach(String headerName, Iterable<Map.Entry<String, String>> carrier, S state, HeaderConsumer<String, S> consumer) {
        for (Map.Entry<String, String> entry : carrier) {
            if (entry.getKey().equalsIgnoreCase(headerName)) {
                consumer.accept(entry.getValue(), state);
            }
        }
    }

    @Override
    public void setHeader(String headerName, String headerValue, Map<String, String> textMap) {
        textMap.put(headerName, headerValue);
    }
}
