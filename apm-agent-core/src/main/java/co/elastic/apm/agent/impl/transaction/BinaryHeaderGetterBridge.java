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

import co.elastic.apm.tracer.api.dispatch.HeaderGetter;

import javax.annotation.Nullable;

public class BinaryHeaderGetterBridge<C> implements BinaryHeaderGetter<C> {

    private final co.elastic.apm.tracer.api.dispatch.BinaryHeaderGetter<C> binaryHeaderGetter;

    public BinaryHeaderGetterBridge(co.elastic.apm.tracer.api.dispatch.BinaryHeaderGetter<C> binaryHeaderGetter) {
        this.binaryHeaderGetter = binaryHeaderGetter;
    }

    @Nullable
    @Override
    public byte[] getFirstHeader(String headerName, C carrier) {
        return binaryHeaderGetter.getFirstHeader(headerName, carrier);
    }

    @Override
    public <S> void forEach(String headerName, C carrier, S state, co.elastic.apm.agent.impl.transaction.HeaderGetter.HeaderConsumer<byte[], S> consumer) {
        binaryHeaderGetter.forEach(headerName, carrier, state, consumer);
    }

    @Override
    public <S> void forEach(String headerName, C carrier, S state, HeaderGetter.HeaderConsumer<byte[], S> consumer) {
        binaryHeaderGetter.forEach(headerName, carrier, state, consumer);
    }
}
