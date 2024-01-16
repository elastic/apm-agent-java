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
package co.elastic.apm.agent.webfluxcommon;

import org.springframework.web.reactive.function.server.ServerResponse;

import javax.annotation.Nullable;

/**
 * This class must be located within the same package as used by the WebFlux instrumentation, otherwise it will be loaded by the
 * agent class loader, rather than the WebFlux plugin class loader. If the {@link ISpringWebVersionUtils} implementations are loaded
 * anywhere other than the WebFlux plugin class loader, their loading will cause a {@link LinkageError}.
 */
public class SpringWebVersionUtils {

    private static final String SPRING_WEB_5_UTILS_CLASS_NAME = "co.elastic.apm.agent.webfluxcommon.SpringWeb5Utils";
    private static final String SPRING_WEB_6_UTILS_CLASS_NAME = "co.elastic.apm.agent.webfluxcommon.SpringWeb6Utils";

    @Nullable
    private static ISpringWebVersionUtils instance = null;

    private static volatile boolean initialized = false;

    private static synchronized void initialize() throws Exception {
        if (initialized) {
            return;
        }
        try {
            Class<?> statusCodeType = ServerResponse.class.getMethod("statusCode").getReturnType();
            switch (statusCodeType.getName()) {
                case "org.springframework.http.HttpStatusCode": // spring 6.0.0 or higher
                    instance = (ISpringWebVersionUtils) Class.forName(SPRING_WEB_6_UTILS_CLASS_NAME).getDeclaredConstructor().newInstance();
                    break;
                case "org.springframework.http.HttpStatus": // spring < 6
                    instance = (ISpringWebVersionUtils) Class.forName(SPRING_WEB_5_UTILS_CLASS_NAME).getDeclaredConstructor().newInstance();
                    break;
                default:
                    throw new IllegalStateException("Unexpected type: "+statusCodeType.getName());
            }
        } finally {
            initialized = true;
        }
    }

    @Nullable
    private static ISpringWebVersionUtils getImplementation() throws Exception {
        if (!initialized) {
            initialize();
        }
        return instance;
    }

    /**
     * A utility method to get the status code of a {@code org.springframework.http.server.reactive.ServerHttpResponse} from any version
     * of Spring framework.
     *
     * @param response must be of type {@code org.springframework.http.server.reactive.ServerHttpResponse}, otherwise an Exception is
     *                 expected
     * @return the status code of the provided response
     */
    public static int getServerStatusCode(Object response) throws Exception {
        ISpringWebVersionUtils implementation = getImplementation();
        if (implementation != null) {
            return implementation.getServerStatusCode(response);
        }
        return 200;
    }


    /**
     * A utility method to get the status code of a {@code org.springframework.web.reactive.function.client.ClientResponse} from any version
     * of Spring framework.
     *
     * @param response must be of type {@code org.springframework.web.reactive.function.client.ClientResponse}, otherwise an Exception is
     *                 expected
     * @return the status code of the provided response
     */
    public static int getClientStatusCode(Object response) throws Exception {
        ISpringWebVersionUtils implementation = getImplementation();
        if (implementation != null) {
            return implementation.getClientStatusCode(response);
        }
        return 200;
    }

    public interface ISpringWebVersionUtils {
        /**
         * A utility method to get the status code of a {@code org.springframework.http.server.reactive.ServerHttpResponse} from any version
         * of Spring framework.
         *
         * @param response must be of type {@code org.springframework.http.server.reactive.ServerHttpResponse}
         * @return the corresponding status code
         */
        int getServerStatusCode(Object response);

        /**
         * A utility method to get the status code of a {@code org.springframework.web.reactive.function.client.ClientResponse} from any version
         * of Spring framework.
         *
         * @param response must be of type {@code org.springframework.web.reactive.function.client.ClientResponse}
         * @return the corresponding status code
         */
        int getClientStatusCode(Object response);

    }
}
