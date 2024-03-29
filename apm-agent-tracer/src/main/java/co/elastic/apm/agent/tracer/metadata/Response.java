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
package co.elastic.apm.agent.tracer.metadata;

import javax.annotation.Nullable;
import java.util.Collection;

public interface Response {

    /**
     * A mapping of HTTP headers of the response object
     */
    PotentiallyMultiValuedMap getHeaders();

    /**
     * A boolean indicating whether the response was finished or not
     */
    Response withFinished(boolean finished);

    /**
     * The HTTP status code of the response.
     */
    Response withStatusCode(int statusCode);

    Response withHeadersSent(boolean headersSent);

    Response addHeader(String headerName, @Nullable String headerValue);

    Response addHeader(String headerName, @Nullable Collection<String> headerValues);
}
