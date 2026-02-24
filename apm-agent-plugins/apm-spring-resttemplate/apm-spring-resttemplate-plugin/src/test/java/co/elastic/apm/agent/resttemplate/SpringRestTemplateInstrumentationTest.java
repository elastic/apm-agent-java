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
package co.elastic.apm.agent.resttemplate;

import co.elastic.apm.agent.common.JvmRuntimeInfo;
import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.ClassUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public class SpringRestTemplateInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private static final Map<Class<? extends ClientHttpRequestFactory>, Boolean> factories = new HashMap<>();
    {
        factories.put(SimpleClientHttpRequestFactory.class, true);
        factories.put(HttpComponentsClientHttpRequestFactory.class, true);
        try {
            factories.put((Class<? extends ClientHttpRequestFactory>) ClassUtils.forName("org.springframework.http.client.OkHttp3ClientHttpRequestFactory", getClass().getClassLoader()), false);
        } catch (ClassNotFoundException cnfe) {
            // Ignore
        }

    }

    // Cannot directly reference RestTemplate here because it is compiled with Java 17
    private final Object restTemplate;

    public SpringRestTemplateInstrumentationTest(Supplier<RestTemplate> supplier) {
        restTemplate = supplier.get();
    }

    @Parameterized.Parameters()
    public static Iterable<Supplier<RestTemplate>> data() {
        if (JvmRuntimeInfo.ofCurrentVM().getMajorVersion() >= 17) {
            return Java17Code.getRestTemplateFactories();
        } else {
            return List.of();
        }
    }

    @Override
    protected void performGet(String path) {
        Java17Code.performGet(restTemplate, path);
    }

    @Override
    protected boolean isBodyCapturingSupported() {
        return Java17Code.isBodyCapturingSupported(restTemplate);
    }

    @Override
    public boolean isTestHttpCallWithUserInfoEnabled() {
        return Java17Code.isTestHttpCallWithUserInfoEnabled(restTemplate);
    }

    @Override
    protected void performPost(String path, byte[] content, String contentTypeHeader) throws Exception {
        Java17Code.performPost(restTemplate, path, content, contentTypeHeader);
    }

    /**
     * The code is compiled with java 17 but potentially run with java 11.
     * JUnit will inspect the test class, therefore it must not contain any references to java 17 code.
     */
    private static class Java17Code {
        public static void performGet(Object restTemplate, String path) {
            // note: getForEntity is only available as of Spring-web 3.0.2
            ((RestTemplate) restTemplate).getForEntity(path, String.class);
        }

        public static void performPost(Object restTemplateObj, String path, byte[] content, String contentTypeHeader) {
            RestTemplate restTemplate = (RestTemplate) restTemplateObj;
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", contentTypeHeader);
            HttpEntity<byte[]> entity = new HttpEntity<byte[]>(content, headers);
            restTemplate.exchange(path, HttpMethod.POST, entity, String.class);
        }

        public static Iterable<Supplier<RestTemplate>> getRestTemplateFactories() {
            return factories.keySet()
                .stream()
                .map((clz) -> BeanUtils.instantiateClass(clz))
                .map( (fac) -> (Supplier<RestTemplate>) () -> new RestTemplate(fac)).collect(Collectors.toList());
        }

        public static boolean isBodyCapturingSupported(Object restTemplateObj) {
            RestTemplate restTemplate = (RestTemplate) restTemplateObj;
            return factories.get(restTemplate.getRequestFactory().getClass());
        }

        public static boolean isTestHttpCallWithUserInfoEnabled(Object restTemplateObj) {
            RestTemplate restTemplate = (RestTemplate) restTemplateObj;
            if (restTemplate.getRequestFactory() instanceof HttpComponentsClientHttpRequestFactory) {
                // newer http components don't support userinfo in URI anymore:
                // I/O error on GET request for "http://user:passwd@localhost:50931/": Request URI authority contains deprecated userinfo component
                return false;
            }
            return true;
        }

    }
}
