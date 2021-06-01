/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.springwebflux;

import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import org.springframework.http.HttpHeaders;

import javax.annotation.Nullable;
import java.util.List;

public class HeaderGetter implements TextHeaderGetter<HttpHeaders> {

    @Nullable
    @Override
    public String getFirstHeader(String headerName, HttpHeaders carrier) {
        return carrier.getFirst(headerName);
    }

    @Override
    public <S> void forEach(String headerName, HttpHeaders carrier, S state, HeaderConsumer<String, S> consumer) {
        List<String> values = carrier.get(headerName);
        for (int i = 0; i < values.size(); i++) {
            consumer.accept(values.get(i), state);
        }
    }
}
