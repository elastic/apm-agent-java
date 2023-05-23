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
package co.elastic.apm.agent.servlet.helper;

import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Enumeration;

public class JakartaServletRequestHeaderGetter extends AbstractServletRequestHeaderGetter<HttpServletRequest> {

    private static final JakartaServletRequestHeaderGetter INSTANCE = new JakartaServletRequestHeaderGetter();

    public static TextHeaderGetter<HttpServletRequest> getInstance() {
        return INSTANCE;
    }

    @Override
    public String getFirstHeader(String headerName, HttpServletRequest carrier) {
        return carrier.getHeader(headerName);
    }

    @Override
    Enumeration<String> getHeaders(String headerName, HttpServletRequest carrier) {
        return carrier.getHeaders(headerName);
    }
}
