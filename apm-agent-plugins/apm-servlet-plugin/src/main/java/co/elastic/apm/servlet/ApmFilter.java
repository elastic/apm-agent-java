/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.servlet;

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.context.Request;
import co.elastic.apm.impl.context.Response;
import co.elastic.apm.impl.transaction.Transaction;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.Enumeration;

/**
 * @deprecated using the ApmFilter is deprecated as there is support for instrumentation
 */
@Deprecated
public class ApmFilter implements Filter {

    private final ServletTransactionHelper servletTransactionHelper;
    private final ElasticApmTracer tracer;

    public ApmFilter() {
        this(ElasticApmTracer.get());
    }

    public ApmFilter(ElasticApmTracer tracer) {
        this.tracer = tracer;
        this.servletTransactionHelper = new ServletTransactionHelper(this.tracer);
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            filterChain.doFilter(request, response);
        } else {
            final HttpServletRequest httpServletRequest = (HttpServletRequest) request;
            final HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            captureTransaction(httpServletRequest, httpServletResponse, filterChain);
        }
    }

    void captureTransaction(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        final Transaction transaction = servletTransactionHelper.onBefore(request.getServletPath(), request.getPathInfo(),
            request.getRequestURI(), request.getHeader("User-Agent"));
        Exception exception = null;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | RuntimeException | ServletException e) {
            exception = e;
            throw e;
        } finally {
            if (transaction != null) {
                final Request req = transaction.getContext().getRequest();
                if (request.getCookies() != null) {
                    for (Cookie cookie : request.getCookies()) {
                        req.addCookie(cookie.getName(), cookie.getValue());
                    }
                }
                final Enumeration headerNames = request.getHeaderNames();
                while (headerNames.hasMoreElements()) {
                    final String headerName = (String) headerNames.nextElement();
                    req.addHeader(headerName, request.getHeader(headerName));
                }
                final Response resp = transaction.getContext().getResponse();
                for (String headerName : response.getHeaderNames()) {
                    resp.addHeader(headerName, response.getHeader(headerName));
                }
                final Principal userPrincipal = request.getUserPrincipal();
                servletTransactionHelper.onAfter(transaction, exception, userPrincipal != null ? userPrincipal.getName() : null,
                    request.getProtocol(), request.getMethod(), request.isSecure(), request.getScheme(), request.getServerName(),
                    request.getServerPort(), request.getRequestURI(), request.getQueryString(), request.getParameterMap(),
                    request.getRemoteAddr(), request.getRequestURL(), response.isCommitted(), response.getStatus());
            }
        }
    }

    @Override
    public void destroy() {
        tracer.stop();
    }
}
