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
package co.elastic.apm.agent.httpclient.helper;

import co.elastic.apm.agent.impl.transaction.AbstractTextHeaderGetterTest;
import org.apache.http.HttpRequest;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;

import java.util.List;
import java.util.Map;

class RequestHeaderAccessorTest extends AbstractTextHeaderGetterTest<RequestHeaderAccessor, HttpRequest> {

    @Override
    protected RequestHeaderAccessor createTextHeaderGetter() {
        return RequestHeaderAccessor.INSTANCE;
    }

    @Override
    protected HttpRequest createCarrier(Map<String, List<String>> map) {
        HttpRequest request = new BasicHttpRequest("GET", "http://fake/");
        map.forEach((k, values) -> values.forEach(v -> request.addHeader(new BasicHeader(k, v))));
        return request;
    }
}
