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
package co.elastic.apm.agent.springwebflux;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;

/**
 * This class is compiled with spring-web 5.x, relying on the {@link ServerHttpResponse#getStatusCode()}, which changed in 6.0.0.
 * Therefore, it MUST only be loaded through its class name through {@link SpringWebVersionUtils}.
 */
@SuppressWarnings("unused") //Created via reflection
public class SpringWeb5Utils implements SpringWebVersionUtils.ISpringWebVersionUtils {

    @Override
    public int getStatusCode(Object response) {
        HttpStatus statusCode = ((ServerHttpResponse) response).getStatusCode();
        return statusCode != null ? statusCode.value() : 200;
    }
}
