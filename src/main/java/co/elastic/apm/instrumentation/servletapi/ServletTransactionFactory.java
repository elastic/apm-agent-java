package co.elastic.apm.instrumentation.servletapi;

import co.elastic.apm.intake.Context;
import co.elastic.apm.intake.Request;
import co.elastic.apm.intake.Response;
import co.elastic.apm.intake.User;
import co.elastic.apm.intake.errors.Stacktrace;
import co.elastic.apm.intake.transactions.Span;
import co.elastic.apm.intake.transactions.Transaction;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ServletTransactionFactory {

    private static final HashMap<Integer, String> STATUS_CODES_AS_STRING = new HashMap<>();

    static {
        for (int i = 100; i < 600; i++) {
            STATUS_CODES_AS_STRING.put(i, Integer.toString(1));
        }
    }

    public static Transaction createTransaction(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, long durationNanos) {
        Transaction transaction = Transaction.create();
        Context context = transaction.getContext();
        fillRequest(transaction.getContext().getRequest(), httpServletRequest);
        fillResponse(context.getResponse(), httpServletResponse);
        fillUser(context.getUser(), httpServletRequest);

        transaction.withDuration(durationNanos / 1_000_000d);
        transaction.withId(UUID.randomUUID().toString());
        // TODO can this be set by apm-server when there is no explicit name set?
        transaction.withName(httpServletRequest.getRequestURI());
        transaction.withResult(getStatusCodeAsString(httpServletResponse.getStatus()));
        transaction.withSampled(true);
        transaction.withTimestamp(java.lang.System.currentTimeMillis());
        transaction.withType("request");
        transaction.getSpanCount().getDropped().withTotal(0);
//        transaction.getSpans().add(createSpan());
        return transaction;
    }

    public static String getRequestNameByRequest(HttpServletRequest request) {
        return request.getMethod() + " " + removeSemicolonContent(request.getRequestURI().substring(request.getContextPath().length()));
    }

    /*
     * copy of org.springframework.web.util.UrlPathHelper#removeSemicolonContentInternal
     */
    public static String removeSemicolonContent(String requestUri) {
        int semicolonIndex = requestUri.indexOf(';');
        while (semicolonIndex != -1) {
            int slashIndex = requestUri.indexOf('/', semicolonIndex);
            String start = requestUri.substring(0, semicolonIndex);
            requestUri = (slashIndex != -1) ? start + requestUri.substring(slashIndex) : start;
            semicolonIndex = requestUri.indexOf(';', semicolonIndex);
        }
        return requestUri;
    }

    private static String getStatusCodeAsString(int status) {
        final String statusCodeAsString = STATUS_CODES_AS_STRING.get(status);
        if (statusCodeAsString == null) {
            return Integer.toString(status);
        } else {
            return statusCodeAsString;
        }

    }

    private static Span createSpan() {
        Span span = new Span();
        // span.id long
        span.withId(ThreadLocalRandom.current().nextLong());
        span.withDuration(3.781912);
        span.withStart(2.83092);
        span.withName("SELECT FROM product_types");
        span.withType("db.postgresql.query");
        fillSpanContext(span.getContext());
        createStackTrace(span.getStacktrace(), new Exception().getStackTrace());
        // TODO Integer
        // distributed tracing: parent-id span id or transaction id?
        // span.withParent(null);
        return span;
    }

    private static void createStackTrace(ArrayList<Stacktrace> stacktrace, StackTraceElement[] stackTrace) {
        stacktrace.ensureCapacity(stackTrace.length);
        for (StackTraceElement stackTraceElement : stackTrace) {
            // TODO no allocation
            Stacktrace s = new Stacktrace();
            s.withAbsPath(stackTraceElement.getClassName());
            s.withFilename(stackTraceElement.getFileName());
            s.withFunction(stackTraceElement.getMethodName());
            s.withLineno(stackTraceElement.getLineNumber());
            s.withLibraryFrame(true);
            // TODO Java 9 only
            // s.withModule(stackTraceElement.getModuleName());
            stacktrace.add(s);
        }
    }

    private static void fillSpanContext(co.elastic.apm.intake.transactions.Context context) {
        context.getDb().withInstance("customers")
            .withStatement("SELECT * FROM product_types WHERE user_id=?")
            .withType("sql")
            .withUser("readonly_user");
    }

    private static void fillUser(User user, HttpServletRequest httpServletRequest) {
        user.withUsername(getUserName(httpServletRequest));
        // TODO
//        user.withId("99");
//        user.withEmail("foo@example.com");
    }

    private static String getUserName(HttpServletRequest httpServletRequest) {
        final Principal userPrincipal = httpServletRequest.getUserPrincipal();
        return userPrincipal != null ? userPrincipal.getName() : null;
    }

    private static Response fillResponse(Response response, HttpServletResponse httpServletResponse) {
        response.withFinished(true);
        fillResponseHeaders(httpServletResponse, response.getHeaders());
        response.withHeadersSent(httpServletResponse.isCommitted());
        response.withStatusCode(httpServletResponse.getStatus());
        return response;
    }


    private static Map<String, String> fillResponseHeaders(HttpServletResponse response, Map<String, String> headers) {
        final Collection<String> headerNames = response.getHeaderNames();
        for (String headerName : headerNames) {
            headers.put(headerName, response.getHeader(headerName));
        }
        return headers;
    }

    private static Request fillRequest(Request request, HttpServletRequest httpServletRequest) {
        if ("application/x-www-form-urlencoded".equals(httpServletRequest.getHeader(" content type"))) {
            for (Map.Entry<String, String[]> params : httpServletRequest.getParameterMap().entrySet()) {
                request.withFormUrlEncodedParameters(params.getKey(), params.getValue());
            }
        } else {
            // TODO rawBody -> wrapper for input stream on HttpServletRequest
        }
        Cookie[] cookies = httpServletRequest.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                request.getCookies().put(cookie.getName(), cookie.getValue());
            }
        }
        // TODO multi valued headers?
        fillHeaders(httpServletRequest, request.getHeaders());
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

        return request;
    }

    private static Map<String, String> fillHeaders(HttpServletRequest request, Map<String, String> headers) {
        final Enumeration headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            final String headerName = (String) headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        return headers;
    }

    private static String getRawURL(final HttpServletRequest request) {
        final String requestURI = request.getRequestURI();
        final String queryString = request.getQueryString();

        if (queryString == null) {
            return requestURI;
        } else {
            return requestURI + '?' + queryString;
        }
    }

    public static String getFullURL(final HttpServletRequest request) {
        final StringBuffer requestURL = request.getRequestURL();
        final String queryString = request.getQueryString();

        if (queryString == null) {
            return requestURL.toString();
        } else {
            return requestURL.append('?').append(queryString).toString();
        }
    }

    private static String getPortAsString(HttpServletRequest httpServletRequest) {
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

    private static String getHttpVersion(HttpServletRequest httpRequest) {
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

}
