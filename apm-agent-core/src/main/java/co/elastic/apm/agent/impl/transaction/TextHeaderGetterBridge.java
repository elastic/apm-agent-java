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
package co.elastic.apm.agent.impl.transaction;

import javax.annotation.Nullable;

public class TextHeaderGetterBridge<C> implements TextHeaderGetter<C> {

    private final co.elastic.apm.tracer.api.dispatch.TextHeaderGetter<C> textHeadersGetter;

    public TextHeaderGetterBridge(co.elastic.apm.tracer.api.dispatch.TextHeaderGetter<C> textHeadersGetter) {
        this.textHeadersGetter = textHeadersGetter;
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, C carrier) {
        return textHeadersGetter.getFirstHeader(headerName, carrier);
    }

    @Override
    public <S> void forEach(String headerName, C carrier, S state, HeaderGetter.HeaderConsumer<String, S> consumer) {
        textHeadersGetter.forEach(headerName, carrier, state, consumer);
    }

    @Override
    public <S> void forEach(String headerName, C carrier, S state, co.elastic.apm.tracer.api.dispatch.HeaderGetter.HeaderConsumer<String, S> consumer) {
        textHeadersGetter.forEach(headerName, carrier, state, consumer);
    }
}
