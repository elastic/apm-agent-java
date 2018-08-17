/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.restclient;

import co.elastic.apm.httpclient.AbstractHttpClientInstrumentationTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.function.Supplier;

@RunWith(Parameterized.class)
public class SpringRestTemplateInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private RestTemplate restTemplate;

    public SpringRestTemplateInstrumentationTest(Supplier<ClientHttpRequestFactory> supplier) {
        restTemplate = new RestTemplate(supplier.get());
    }

    @Parameterized.Parameters()
    public static Iterable<Supplier<ClientHttpRequestFactory>> data() {
        return Arrays.asList(
            SimpleClientHttpRequestFactory::new,
            OkHttp3ClientHttpRequestFactory::new,
            HttpComponentsClientHttpRequestFactory::new);
    }

    @Override
    protected void performGet(String path) {
        restTemplate.getForEntity(path, Void.class);
    }
}
