package co.elastic.apm.servlet;

import co.elastic.apm.impl.Context;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.Request;
import co.elastic.apm.impl.Response;
import co.elastic.apm.impl.Transaction;
import co.elastic.apm.impl.User;

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

    public ApmFilter() {
        this(ElasticApmTracer.builder().build().register());
    }

    public ApmFilter(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            final Transaction transaction = tracer.startTransaction();
            try {
                filterChain.doFilter(request, response);
            } finally {
                fillTransaction(transaction, httpRequest, (HttpServletResponse) response);
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

        // TODO can this be set by apm-server when there is no explicit name set?
        transaction.withName(httpServletRequest.getRequestURI());
        transaction.withResult(getResult(httpServletResponse.getStatus()));
        transaction.withSampled(true);
        transaction.withTimestamp(System.currentTimeMillis());
        transaction.withType("request");
        transaction.getSpanCount().getDropped().withTotal(0);
    }

    private String getRequestNameByRequest(HttpServletRequest request) {
        return request.getMethod() + " " + removeSemicolonContent(request.getRequestURI().substring(request.getContextPath().length()));
    }

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

    /*
     * copy of org.springframework.web.util.UrlPathHelper#removeSemicolonContentInternal
     */
    private String removeSemicolonContent(String requestUri) {
        int semicolonIndex = requestUri.indexOf(';');
        while (semicolonIndex != -1) {
            int slashIndex = requestUri.indexOf('/', semicolonIndex);
            String start = requestUri.substring(0, semicolonIndex);
            requestUri = (slashIndex != -1) ? start + requestUri.substring(slashIndex) : start;
            semicolonIndex = requestUri.indexOf(';', semicolonIndex);
        }
        return requestUri;
    }

    private void fillUser(User user, HttpServletRequest httpServletRequest) {
        user.withUsername(getUserName(httpServletRequest));
    }

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
        if ("application/x-www-form-urlencoded".equals(httpServletRequest.getHeader("content-type"))) {
            for (Map.Entry<String, String[]> params : httpServletRequest.getParameterMap().entrySet()) {
                request.withFormUrlEncodedParameters(params.getKey(), params.getValue());
            }
        } else {
            // TODO rawBody -> wrapper for input stream on HttpServletRequest
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
        // TODO can this be filled by apm-server?
        //.withFull(getFullURL(httpServletRequest))
        //.withRaw(getRawURL(httpServletRequest));
    }

    private void fillHeaders(HttpServletRequest servletRequest, Request request) {
        final Enumeration headerNames = servletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            final String headerName = (String) headerNames.nextElement();
            request.addHeader(headerName, servletRequest.getHeader(headerName));
        }
    }

    private String getRawURL(final HttpServletRequest request) {
        final String requestURI = request.getRequestURI();
        final String queryString = request.getQueryString();

        if (queryString == null) {
            return requestURI;
        } else {
            return requestURI + '?' + queryString;
        }
    }

    public String getFullURL(final HttpServletRequest request) {
        final StringBuffer requestURL = request.getRequestURL();
        final String queryString = request.getQueryString();

        if (queryString == null) {
            return requestURL.toString();
        } else {
            return requestURL.append('?').append(queryString).toString();
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
