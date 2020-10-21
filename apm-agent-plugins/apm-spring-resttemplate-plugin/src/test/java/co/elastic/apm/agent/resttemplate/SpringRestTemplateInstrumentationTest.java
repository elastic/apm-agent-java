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

import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class SpringRestTemplateInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private RestTemplate restTemplate;

    public static Stream<Arguments> args() {
        Iterable<Supplier<ClientHttpRequestFactory>> iterable = Arrays.asList(
            SimpleClientHttpRequestFactory::new,
            OkHttp3ClientHttpRequestFactory::new,
            HttpComponentsClientHttpRequestFactory::new);
        return StreamSupport.stream(iterable.spliterator(), false)
            .map(k -> Arguments.of(k))
            .collect(Collectors.toList())
            .stream();
    }

    @Override
    public void setUp(Object arg) {
        Supplier<ClientHttpRequestFactory> supplier = (Supplier<ClientHttpRequestFactory>) arg;
        restTemplate = new RestTemplate(supplier.get());
    }

    @Override
    protected void performGet(String path) {
        restTemplate.getForEntity(path, Void.class);
    }
}
