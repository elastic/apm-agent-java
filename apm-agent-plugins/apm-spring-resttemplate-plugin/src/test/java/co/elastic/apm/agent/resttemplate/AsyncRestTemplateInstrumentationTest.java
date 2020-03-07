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
package co.elastic.apm.agent.resttemplate;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.junit.BeforeClass;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.client.AsyncRestTemplate;

import java.util.concurrent.ExecutionException;

public class AsyncRestTemplateInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private static AsyncRestTemplate asyncRestTemplate;

    @BeforeClass
    public static void setUp() {
        asyncRestTemplate = new AsyncRestTemplate();
    }

    @Override
    protected void performGet(String path) throws ExecutionException, InterruptedException {
        ListenableFuture<ResponseEntity<Void>> future = asyncRestTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(""), Void.class);
        //waits for the result
        future.get();
    }
}
