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

import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.servlet.helper.JavaxServletRequestHeaderGetter;

import javax.annotation.Nullable;
import javax.servlet.DispatcherType;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map;

public class JavaxServletApiAdapter implements ServletApiAdapter<HttpServletRequest, HttpServletResponse, ServletContext, ServletContextEvent, FilterConfig, ServletConfig> {

    private static final JavaxServletApiAdapter INSTANCE = new JavaxServletApiAdapter();

    private JavaxServletApiAdapter() {
    }

    public static JavaxServletApiAdapter get() {
        return INSTANCE;
    }

    @Override
    public HttpServletRequest asHttpServletRequest(Object servletRequest) {
        if (servletRequest instanceof HttpServletRequest) {
            return (HttpServletRequest) servletRequest;
        }
        return null;
    }

    @Nullable
    @Override
    public HttpServletResponse asHttpServletResponse(Object servletResponse) {
        if (servletResponse instanceof HttpServletResponse) {
            return (HttpServletResponse) servletResponse;
        }
        return null;
    }

    @Override
    public boolean isRequestDispatcherType(HttpServletRequest servletRequest) {
        return servletRequest.getDispatcherType() == DispatcherType.REQUEST;
    }

    @Override
    public boolean isAsyncDispatcherType(HttpServletRequest servletRequest) {
        return servletRequest.getDispatcherType() == DispatcherType.ASYNC;
    }

    @Override
    public boolean isForwardDispatcherType(HttpServletRequest servletRequest) {
        return servletRequest.getDispatcherType() == DispatcherType.FORWARD;
    }

    @Override
    public boolean isIncludeDispatcherType(HttpServletRequest servletRequest) {
        return servletRequest.getDispatcherType() == DispatcherType.INCLUDE;
    }

    @Override
    public boolean isErrorDispatcherType(HttpServletRequest servletRequest) {
        return servletRequest.getDispatcherType() == DispatcherType.ERROR;
    }

    @Nullable
    @Override
    public ClassLoader getClassLoader(@Nullable ServletContext servletContext) {
        if (servletContext == null) {
            return null;
        }

        // getClassloader might throw UnsupportedOperationException
        // see Section 4.4 of the Servlet 3.0 specification
        try {
            return servletContext.getClassLoader();
        } catch (UnsupportedOperationException ignored) {
            return null;
        }
    }

    @Override
    public String getServletContextName(ServletContext servletContext) {
        return servletContext.getServletContextName();
    }

    @Nullable
    @Override
    public String getContextPath(ServletContext servletContext) {
        return servletContext.getContextPath();
    }

    @Override
    public void handleCookies(Request request, HttpServletRequest servletRequest) {
        if (servletRequest.getCookies() != null) {
            for (Cookie cookie : servletRequest.getCookies()) {
                request.addCookie(cookie.getName(), cookie.getValue());
            }
        }
    }

    @Override
    public Enumeration<String> getRequestHeaderNames(HttpServletRequest servletRequest) {
        return servletRequest.getHeaderNames();
    }

    @Override
    public Enumeration<String> getRequestHeaders(HttpServletRequest servletRequest, String name) {
        return servletRequest.getHeaders(name);
    }

    @Override
    public String getHeader(HttpServletRequest httpServletRequest, String name) {
        return httpServletRequest.getHeader(name);
    }

    @Override
    public String getProtocol(HttpServletRequest servletRequest) {
        return servletRequest.getProtocol();
    }

    @Override
    public String getMethod(HttpServletRequest servletRequest) {
        return servletRequest.getMethod();
    }

    @Override
    public boolean isSecure(HttpServletRequest servletRequest) {
        return servletRequest.isSecure();
    }

    @Override
    public String getScheme(HttpServletRequest servletRequest) {
        return servletRequest.getScheme();
    }

    @Override
    public String getServerName(HttpServletRequest servletRequest) {
        return servletRequest.getServerName();
    }

    @Override
    public int getServerPort(HttpServletRequest servletRequest) {
        return servletRequest.getServerPort();
    }

    @Override
    public String getRequestURI(HttpServletRequest servletRequest) {
        return servletRequest.getRequestURI();
    }

    @Override
    public String getQueryString(HttpServletRequest servletRequest) {
        return servletRequest.getQueryString();
    }

    @Override
    public String getRemoteAddr(HttpServletRequest servletRequest) {
        return servletRequest.getRemoteAddr();
    }

    @Override
    public String getServletPath(HttpServletRequest servletRequest) {
        return servletRequest.getServletPath();
    }

    @Override
    public String getPathInfo(HttpServletRequest servletRequest) {
        return servletRequest.getPathInfo();
    }

    @Override
    public Object getIncludeServletPathAttribute(HttpServletRequest servletRequest) {
        return servletRequest.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
    }

    @Override
    public Object getIncludePathInfoAttribute(HttpServletRequest servletRequest) {
        return servletRequest.getAttribute(RequestDispatcher.INCLUDE_PATH_INFO);
    }

    @Override
    public boolean isInstanceOfHttpServlet(Object object) {
        return object instanceof HttpServlet;
    }

    @Override
    public ServletContext getServletContextFromServletContextEvent(ServletContextEvent servletContextEvent) {
        return servletContextEvent.getServletContext();
    }

    @Override
    public ServletContext getServletContextFromServletConfig(ServletConfig filterConfig) {
        return filterConfig.getServletContext();
    }

    @Override
    public Principal getUserPrincipal(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getUserPrincipal();
    }

    @Nullable
    @Override
    public Object getAttribute(HttpServletRequest servletRequest, String attributeName) {
        return servletRequest.getAttribute(attributeName);
    }

    @Nullable
    @Override
    public Object getHttpAttribute(HttpServletRequest httpServletRequest, String attributeName) {
        return httpServletRequest.getAttribute(attributeName);
    }

    @Override
    public Collection<String> getHeaderNames(HttpServletResponse httpServletResponse) {
        return httpServletResponse.getHeaderNames();
    }

    @Override
    public Collection<String> getHeaders(HttpServletResponse httpServletResponse, String headerName) {
        return httpServletResponse.getHeaders(headerName);
    }

    @Override
    public Map<String, String[]> getParameterMap(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getParameterMap();
    }

    @Override
    public boolean isCommitted(HttpServletResponse httpServletResponse) {
        return httpServletResponse.isCommitted();
    }

    @Override
    public int getStatus(HttpServletResponse httpServletResponse) {
        return httpServletResponse.getStatus();
    }

    @Override
    public ServletContext getServletContext(HttpServletRequest servletRequest) {
        return servletRequest.getServletContext();
    }

    @Nullable
    @Override
    public InputStream getResourceAsStream(ServletContext servletContext, String path) {
        return servletContext.getResourceAsStream(path);
    }

    @Override
    public TextHeaderGetter<HttpServletRequest> getRequestHeaderGetter() {
        return JavaxServletRequestHeaderGetter.getInstance();
    }

    @Override
    public ServletContext getServletContextFromFilterConfig(FilterConfig filterConfig) {
        return filterConfig.getServletContext();
    }
}
