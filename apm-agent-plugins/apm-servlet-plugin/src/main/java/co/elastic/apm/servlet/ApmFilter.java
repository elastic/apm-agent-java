package co.elastic.apm.servlet;

import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.context.Context;
import co.elastic.apm.impl.context.Request;
import co.elastic.apm.impl.context.Response;
import co.elastic.apm.impl.context.Url;
import co.elastic.apm.impl.context.User;
import co.elastic.apm.impl.transaction.Transaction;

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
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map;

public class ApmFilter implements Filter {

    private final ElasticApmTracer tracer;
    private final CoreConfiguration config;

    public ApmFilter() {
        this(ElasticApmTracer.get());
    }

    public ApmFilter(ElasticApmTracer tracer) {
        this.tracer = tracer;
        this.config = tracer.getConfig(CoreConfiguration.class);
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        if (config.isActive()) {
            captureTransaction(request, response, filterChain);
        } else {
            filterChain.doFilter(request, response);
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

        transaction.withName(httpServletRequest.getMethod());
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
        String contentTypeHeader = httpServletRequest.getHeader("content-type");
        if (contentTypeHeader != null && contentTypeHeader.startsWith("application/x-www-form-urlencoded")) {
            for (Map.Entry<String, String[]> params : httpServletRequest.getParameterMap().entrySet()) {
                request.addFormUrlEncodedParameters(params.getKey(), params.getValue());
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
            // TODO reverse proxies? x-request-for
            .withRemoteAddress(httpServletRequest.getRemoteAddr());

        request.getUrl()
            .withProtocol(httpServletRequest.getScheme())
            .withHostname(httpServletRequest.getServerName())
            .withPort(getPortAsString(httpServletRequest))
            .withPathname(httpServletRequest.getRequestURI())
            .withSearch(httpServletRequest.getQueryString());

        fillFullUrl(httpServletRequest, request.getUrl());
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
    }
}
