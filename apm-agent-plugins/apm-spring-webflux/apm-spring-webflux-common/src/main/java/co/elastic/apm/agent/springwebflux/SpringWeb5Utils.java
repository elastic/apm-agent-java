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

public class SpringWeb5Utils implements SpringWebVersionUtils {
    @Override
    public int getStatusCode(ServerHttpResponse response) {
        HttpStatus statusCode = response.getStatusCode();
        return statusCode != null ? statusCode.value() : 200;
    }
}
