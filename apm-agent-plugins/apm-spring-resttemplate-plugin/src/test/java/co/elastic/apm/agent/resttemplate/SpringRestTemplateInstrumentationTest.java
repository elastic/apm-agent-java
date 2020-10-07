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
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class SpringRestTemplateInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private RestTemplate restTemplate;

    public static Stream<Arguments> params() {
        final List<Arguments> configurations = new ArrayList<>();
        configurations.add(Arguments.arguments(new SimpleClientHttpRequestFactory()));
        configurations.add(Arguments.arguments(new OkHttp3ClientHttpRequestFactory()));
        configurations.add(Arguments.arguments(new HttpComponentsClientHttpRequestFactory()));
        return configurations.stream();
    }

    @Override
    public void setUp(Object arg) {
        restTemplate = new RestTemplate((ClientHttpRequestFactory) arg);
    }

    @Override
    protected void performGet(String path) {
        restTemplate.getForEntity(path, Void.class);
    }
}
