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
package co.elastic.apm.agent.servlet.adapter;

import co.elastic.apm.agent.sdk.state.GlobalState;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.metadata.Request;

import javax.annotation.Nullable;
import java.security.Principal;
import java.util.Enumeration;
import java.util.Map;

@GlobalState
public interface ServletRequestAdapter<HttpServletRequest, ServletContext> {
    @Nullable
    HttpServletRequest asHttpServletRequest(Object servletRequest);

    boolean isRequestDispatcherType(HttpServletRequest servletRequest);

    boolean isAsyncDispatcherType(HttpServletRequest servletRequest);

    boolean isForwardDispatcherType(HttpServletRequest servletRequest);

    boolean isIncludeDispatcherType(HttpServletRequest servletRequest);

    boolean isErrorDispatcherType(HttpServletRequest servletRequest);

    @Nullable
    ServletContext getServletContext(HttpServletRequest servletRequest);

    void handleCookies(Request request, HttpServletRequest httpServletRequest);

    @Nullable
    Enumeration<String> getRequestHeaderNames(HttpServletRequest httpServletRequest);

    Enumeration<String> getRequestHeaders(HttpServletRequest httpServletRequest, String name);

    String getHeader(HttpServletRequest httpServletRequest, String name);

    String getProtocol(HttpServletRequest httpServletRequest);

    String getMethod(HttpServletRequest httpServletRequest);

    boolean isSecure(HttpServletRequest servletRequest);

    String getScheme(HttpServletRequest servletRequest);

    String getServerName(HttpServletRequest servletRequest);

    int getServerPort(HttpServletRequest servletRequest);

    String getRequestURI(HttpServletRequest servletRequest);

    String getQueryString(HttpServletRequest servletRequest);

    String getRemoteAddr(HttpServletRequest servletRequest);

    @Nullable
    String getServletPath(HttpServletRequest servletRequest);

    @Nullable
    String getPathInfo(HttpServletRequest servletRequest);

    Object getIncludeServletPathAttribute(HttpServletRequest servletRequest);

    Object getIncludePathInfoAttribute(HttpServletRequest servletRequest);

    @Nullable
    Principal getUserPrincipal(HttpServletRequest httpServletRequest);

    @Nullable
    Object getAttribute(HttpServletRequest request, String attributeName);

    void setAttribute(HttpServletRequest request, String attributeName, Object value);

    @Nullable
    Object getHttpAttribute(HttpServletRequest request, String attributeName);

    Map<String, String[]> getParameterMap(HttpServletRequest httpServletRequest);

    TextHeaderGetter<HttpServletRequest> getRequestHeaderGetter();
}
