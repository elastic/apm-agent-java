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
package co.elastic.apm.agent.dubbo.helper;

import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;

import javax.annotation.Nullable;

public abstract class DubboAttachmentHelper<C> implements TextHeaderSetter<C>, TextHeaderGetter<C> {

    public static final String SEPARATOR = ",";

    @Nullable
    @Override
    public String getFirstHeader(String headerName, C carrier) {
        return doGetHeader(headerName, carrier);
    }

    @Override
    public <S> void forEach(String headerName, C carrier, S state, HeaderConsumer<String, S> consumer) {
        String headerValueStr = doGetHeader(headerName, carrier);
        if (headerValueStr == null) {
            return;
        }
        String[] headerValues = headerValueStr.split(SEPARATOR);
        for (String headerValue : headerValues) {
            consumer.accept(headerValue, state);
        }
    }

    @Override
    public void setHeader(String headerName, String headerValue, C carrier) {
        String oldHeader = getFirstHeader(headerName, carrier);
        String newHeader = headerValue;
        if (oldHeader != null) {
            newHeader = oldHeader + SEPARATOR + headerValue;
        }
        doSetHeader(headerName, newHeader, carrier);
    }

    abstract void doSetHeader(String headerName, String headerValue, C carrier);

    abstract String doGetHeader(String headerName, C carrier);
}
