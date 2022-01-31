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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map;

public interface ServletApiAdapter<ServletRequest, ServletResponse, HttpServletRequest, HttpServletResponse, ServletContext> {

    @Nullable
    HttpServletRequest asHttpServletRequest(ServletRequest servletRequest);

    @Nullable
    HttpServletResponse asHttpServletResponse(ServletResponse servletResponse);

    boolean isRequestDispatcherType(ServletRequest servletRequest);

    boolean isAsyncDispatcherType(ServletRequest servletRequest);

    boolean isForwardDispatcherType(ServletRequest servletRequest);

    boolean isIncludeDispatcherType(ServletRequest servletRequest);

    boolean isErrorDispatcherType(ServletRequest servletRequest);

    @Nullable
    ServletContext getServletContext(HttpServletRequest servletRequest);

    @Nullable
    ClassLoader getClassLoader(@Nullable ServletContext servletContext);

    String getServletContextName(ServletContext servletContext);

    @Nullable
    String getContextPath(ServletContext servletContext);

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

    String getPathInfo(HttpServletRequest servletRequest);

    Object getIncludeServletPathAttribute(HttpServletRequest servletRequest);

    Object getIncludePathInfoAttribute(HttpServletRequest servletRequest);

    boolean isInstanceOfHttpServlet(Object object);

    @Nullable
    Principal getUserPrincipal(HttpServletRequest httpServletRequest);

    @Nullable
    Object getAttribute(ServletRequest request, String attributeName);

    @Nullable
    Object getHttpAttribute(HttpServletRequest request, String attributeName);

    Collection<String> getHeaderNames(HttpServletResponse httpServletResponse);

    Collection<String> getHeaders(HttpServletResponse httpServletResponse, String headerName);

    Map<String, String[]> getParameterMap(HttpServletRequest httpServletRequest);

    boolean isCommitted(HttpServletResponse httpServletResponse);

    int getStatus(HttpServletResponse httpServletResponse);

    @Nullable
    InputStream getResourceAsStream(ServletContext servletContext, String path);

    TextHeaderGetter<HttpServletRequest> getRequestHeaderGetter();
}
