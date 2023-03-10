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

import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;
import io.vertx.core.MultiMap;

import javax.annotation.Nullable;
import java.util.List;

public class MultiMapHeadersGetterSetter implements TextHeaderGetter<MultiMap>, TextHeaderSetter<MultiMap> {
    @Nullable
    @Override
    public String getFirstHeader(String headerName, MultiMap carrier) {
        return carrier.get(headerName);
    }

    @Override
    public <S> void forEach(String headerName, MultiMap carrier, S state, HeaderConsumer<String, S> consumer) {
        List<String> values = carrier.getAll(headerName);
        for (int i = 0, size = values.size(); i < size; i++) {
            consumer.accept(values.get(i), state);
        }
    }

    @Override
    public void setHeader(String headerName, String headerValue, MultiMap carrier) {
        carrier.set(headerName, headerValue);
    }
}
