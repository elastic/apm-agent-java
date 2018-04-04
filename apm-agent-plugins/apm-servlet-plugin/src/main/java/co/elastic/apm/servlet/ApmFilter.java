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

import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.configuration.WebConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.context.Context;
import co.elastic.apm.impl.context.Request;
import co.elastic.apm.impl.context.Response;
import co.elastic.apm.impl.context.Url;
import co.elastic.apm.impl.context.User;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.matcher.WildcardMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static co.elastic.apm.configuration.WebConfiguration.EventType.OFF;

public class ApmFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(ApmFilter.class);

    private final static Set<String> METHODS_WITH_BODY = new HashSet<>(Arrays.asList("POST", "PUT", "PATCH", "DELETE"));
    private final ElasticApmTracer tracer;
    private final CoreConfiguration coreConfiguration;
    private final WebConfiguration webConfiguration;

    public ApmFilter() {
        this(ElasticApmTracer.get());
    }

    public ApmFilter(ElasticApmTracer tracer) {
        this.tracer = tracer;
        this.coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        this.webConfiguration = tracer.getConfig(WebConfiguration.class);
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        if (coreConfiguration.isActive() && !isUrlExcluded(request)) {
            captureTransaction(request, response, filterChain);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private boolean isUrlExcluded(ServletRequest request) {
        if (!(request instanceof HttpServletRequest)) {
            return true;
        }
        for (WildcardMatcher matcher : webConfiguration.getIgnoreUrls()) {
            if (matches((HttpServletRequest) request, matcher)) {
                logger.debug("Not tracing this request as the url {} is ignored by the pattern {}",
                    ((HttpServletRequest) request).getRequestURI(), matcher);
                return true;
            }
        }
        return false;
    }

    private boolean matches(HttpServletRequest request, WildcardMatcher matcher) {
        // request.getRequestURI() might contain the session id
        // see also https://stackoverflow.com/a/21046620/1125055 on why servletPath = pathInfo is a good idea
        if (request.getPathInfo() == null) {
            return matcher.matches(request.getServletPath());
        } else {
            return matcher.matches(request.getServletPath(), request.getPathInfo());
        }
    }

    void captureTransaction(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            final Transaction transaction = tracer.startTransaction();
            try {
                filterChain.doFilter(request, response);
                fillTransaction(transaction, httpRequest, (HttpServletResponse) response);
            } catch (IOException | RuntimeException | ServletException e) {
                tracer.captureException(e);
                throw e;
            } finally {
                transaction.end();
            }
        }
    }

    private void fillTransaction(Transaction transaction, HttpServletRequest httpServletRequest,
                                 HttpServletResponse httpServletResponse) {
        Context context = transaction.getContext();
        fillRequest(transaction.getContext().getRequest(), httpServletRequest);
        fillResponse(context.getResponse(), httpServletResponse);
        fillUser(context.getUser(), httpServletRequest);

        // the HTTP method is not a good transaction name, but better than none...
        if (transaction.getName().length() == 0) {
            transaction.withName(httpServletRequest.getMethod());
        }
        transaction.withResult(getResult(httpServletResponse.getStatus()));
        transaction.withType("request");
    }

    @Nullable
    String getResult(int status) {
        if (status >= 200 && status < 300) {
            return "HTTP 2xx";
        }
        if (status >= 300 && status < 400) {
            return "HTTP 3xx";
        }
        if (status >= 400 && status < 500) {
            return "HTTP 4xx";
        }
        if (status >= 500 && status < 600) {
            return "HTTP 5xx";
        }
        if (status >= 100 && status < 200) {
            return "HTTP 1xx";
        }
        return null;
    }

    private void fillUser(User user, HttpServletRequest httpServletRequest) {
        user.withUsername(getUserName(httpServletRequest));
    }

    @Nullable
    private String getUserName(HttpServletRequest httpServletRequest) {
        final Principal userPrincipal = httpServletRequest.getUserPrincipal();
        return userPrincipal != null ? userPrincipal.getName() : null;
    }

    private void fillResponse(Response response, HttpServletResponse httpServletResponse) {
        response.withFinished(true);
        fillResponseHeaders(httpServletResponse, response);
        response.withHeadersSent(httpServletResponse.isCommitted());
        response.withStatusCode(httpServletResponse.getStatus());
    }


    private void fillResponseHeaders(HttpServletResponse servletResponse, Response response) {
        final Collection<String> headerNames = servletResponse.getHeaderNames();
        for (String headerName : headerNames) {
            response.addHeader(headerName, servletResponse.getHeader(headerName));
        }
    }

    private void fillRequest(Request request, HttpServletRequest httpServletRequest) {
        final WebConfiguration.EventType eventType = webConfiguration.getCaptureBody();
        if (hasBody(httpServletRequest)) {
            if (eventType != OFF) {
                captureBody(request, httpServletRequest);
            } else {
                request.redactBody();
            }
        }
        Cookie[] cookies = httpServletRequest.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                request.addCookie(cookie.getName(), cookie.getValue());
            }
        }
        fillHeaders(httpServletRequest, request);
        request.withHttpVersion(getHttpVersion(httpServletRequest));
        request.withMethod(httpServletRequest.getMethod());

        request.getSocket()
            .withEncrypted(httpServletRequest.isSecure())
            .withRemoteAddress(ClientIpUtils.getRealIp(httpServletRequest));

        request.getUrl()
            .withProtocol(httpServletRequest.getScheme())
            .withHostname(httpServletRequest.getServerName())
            .withPort(getPortAsString(httpServletRequest))
            .withPathname(httpServletRequest.getRequestURI())
            .withSearch(httpServletRequest.getQueryString());

        fillFullUrl(httpServletRequest, request.getUrl());
    }

    private boolean hasBody(HttpServletRequest httpServletRequest) {
        return METHODS_WITH_BODY.contains(httpServletRequest.getMethod()) && httpServletRequest.getHeader("content-type") != null;
    }

    private void captureBody(Request request, HttpServletRequest httpServletRequest) {
        String contentTypeHeader = httpServletRequest.getHeader("content-type");
        if (contentTypeHeader != null && contentTypeHeader.startsWith("application/x-www-form-urlencoded")) {
            for (Map.Entry<String, String[]> params : httpServletRequest.getParameterMap().entrySet()) {
                request.addFormUrlEncodedParameters(params.getKey(), params.getValue());
            }
        } else {
            // this content-type is not supported (yet)
            request.redactBody();
        }
    }

    private void fillHeaders(HttpServletRequest servletRequest, Request request) {
        final Enumeration headerNames = servletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            final String headerName = (String) headerNames.nextElement();
            request.addHeader(headerName, servletRequest.getHeader(headerName));
        }
    }

    private void fillFullUrl(final HttpServletRequest request, Url url) {
        // using a StringBuilder to avoid allocations when constructing the full URL
        final StringBuilder fullUrl = url.getFull();
        if (request.getQueryString() != null) {
            fullUrl.ensureCapacity(request.getRequestURL().length() + 1 + request.getQueryString().length());
            fullUrl.append(request.getRequestURL()).append('?').append(request.getQueryString());
        } else {
            fullUrl.ensureCapacity(request.getRequestURL().length());
            fullUrl.append(request.getRequestURL());
        }
    }

    private String getPortAsString(HttpServletRequest httpServletRequest) {
        // don't instantiate objects for common ports
        switch (httpServletRequest.getServerPort()) {
            case 80:
                return "80";
            case 443:
                return "443";
            case 8080:
                return "8080";
            default:
                return Integer.toString(httpServletRequest.getServerPort());
        }
    }

    private String getHttpVersion(HttpServletRequest httpRequest) {
        // don't allocate new strings in the common cases
        switch (httpRequest.getProtocol()) {
            case "HTTP/1.0":
                return "1.0";
            case "HTTP/1.1":
                return "1.1";
            case "HTTP/2.0":
                return "2.0";
            default:
                return httpRequest.getProtocol().replace("HTTP/", "");
        }
    }

    @Override
    public void destroy() {
        tracer.stop();
    }
}
