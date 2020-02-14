/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.impl.transaction.AbstractHeaderGetter;
import co.elastic.apm.agent.impl.transaction.HeaderRemover;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;

import javax.annotation.Nullable;

public class MultiValueMapAccessor extends AbstractHeaderGetter<String, PotentiallyMultiValuedMap> implements
    TextHeaderGetter<PotentiallyMultiValuedMap>, TextHeaderSetter<PotentiallyMultiValuedMap>, HeaderRemover<PotentiallyMultiValuedMap> {

    public static final MultiValueMapAccessor INSTANCE = new MultiValueMapAccessor();

    private MultiValueMapAccessor() {
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, PotentiallyMultiValuedMap headerMap) {
        return headerMap.getFirst(headerName);
    }

    @Override
    public <S> void forEach(String headerName, PotentiallyMultiValuedMap headerMap, S state, HeaderConsumer<String, S> consumer) {
        for (String headerValue : headerMap.getAll(headerName)) {
            consumer.accept(headerValue, state);
        }
    }

    @Override
    public void setHeader(String headerName, String headerValue, PotentiallyMultiValuedMap headerMap) {
        headerMap.add(headerName, headerValue);
    }

    @Override
    public void remove(String headerName, PotentiallyMultiValuedMap headerMap) {
        headerMap.removeIgnoreCase(headerName);
    }
}
