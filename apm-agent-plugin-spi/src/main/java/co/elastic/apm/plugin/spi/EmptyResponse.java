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
package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;
import java.util.Collection;

public class EmptyResponse implements Response {

    public static final Response INSTANCE = new EmptyResponse();

    private EmptyResponse() {
    }

    @Override
    public PotentiallyMultiValuedMap getHeaders() {
        return EmptyPotentiallyMultiValuedMap.INSTANCE;
    }

    @Override
    public Response withFinished(boolean finished) {
        return this;
    }

    @Override
    public Response withStatusCode(int statusCode) {
        return this;
    }

    @Override
    public Response withHeadersSent(boolean headersSent) {
        return this;
    }

    @Override
    public Response addHeader(String headerName, @Nullable Collection<String> headerValues) {
        return this;
    }
}
