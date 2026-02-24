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
package co.elastic.apm.agent.restclient;

import co.elastic.apm.agent.common.JvmRuntimeInfo;
import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(Parameterized.class)
public class SpringRestClientInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    // Cannot directly reference RestTemplate here because it is compiled with Java 17
    private final Object restClient;
    private final Boolean isRedirectFollowingSupported;

    public SpringRestClientInstrumentationTest(Supplier<RestClient> supplier,
                                               Boolean isRedirectFollowingSupported) {
        this.restClient = supplier.get();
        this.isRedirectFollowingSupported = isRedirectFollowingSupported;
    }

    @Parameterized.Parameters()
    public static Iterable<Object[]> data() {
        if (JvmRuntimeInfo.ofCurrentVM().getMajorVersion() >= 17) {
            return SpringRestClientInstrumentationTest.Java17Code.getRestClient();
        } else {
            return List.of();
        }
    }

    @Override
    protected void performGet(String path) {
        Java17Code.performGet(restClient, path);
    }

    @Override
    protected boolean isRedirectFollowingSupported() {
        return isRedirectFollowingSupported;
    }

    /**
     * The code is compiled with java 17 but potentially run with java 11.
     * JUnit will inspect the test class, therefore it must not contain any references to java 17 code.
     */
    public static class Java17Code {
        public static void performGet(Object restClient, String path) {
            ((RestClient) restClient).get().uri(path).retrieve().body(String.class);
        }

        public static Iterable<Object[]> getRestClient() {
            Supplier<RestClient> defaultRestClient = () -> RestClient.create();
            Supplier<RestClient> restTemplateBasedRestClient = () -> RestClient.create(new RestTemplate());
            return Stream.of(
                    new Object[][]{{defaultRestClient, false},
                        {restTemplateBasedRestClient, true}})
                .collect(Collectors.toList());
        }
    }
}
