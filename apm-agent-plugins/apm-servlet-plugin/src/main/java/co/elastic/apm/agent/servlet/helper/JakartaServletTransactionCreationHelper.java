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

import co.elastic.apm.agent.impl.ElasticApmTracer;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;

public class JakartaServletTransactionCreationHelper extends ServletTransactionCreationHelper<HttpServletRequest, ServletContext> {
    public JakartaServletTransactionCreationHelper(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    protected String getServletPath(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getServletPath();
    }

    @Override
    protected String getPathInfo(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getPathInfo();
    }

    @Override
    protected String getHeader(HttpServletRequest httpServletRequest, String headerName) {
        return httpServletRequest.getHeader(headerName);
    }

    @Override
    protected ServletContext getServletContext(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getServletContext();
    }

    @Override
    protected ClassLoader getClassLoader(ServletContext servletContext) {
        return servletContext.getClassLoader();
    }

    @Override
    protected CommonServletRequestHeaderGetter getRequestHeaderGetter() {
        return JakartaServletRequestHeaderGetter.getInstance();
    }
}
