package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.transaction.Transaction;

import javax.annotation.Nullable;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map;

public interface ServletHelper<ServletRequest, ServletResponse, HttpServletRequest, HttpServletResponse, ServletContext> {

    @Nullable
    Transaction getTransactionAttribute(ServletRequest servletRequest);

    boolean isHttpServletRequest(ServletRequest servletRequest);

    boolean isHttpServletResponse(ServletResponse servletResponse);

    boolean isRequestDispatcherType(ServletRequest servletRequest);

    boolean isAsyncDispatcherType(ServletRequest servletRequest);

    boolean isForwardDispatcherType(ServletRequest servletRequest);

    boolean isIncludeDispatcherType(ServletRequest servletRequest);

    boolean isErrorDispatcherType(ServletRequest servletRequest);

    @Nullable
    ServletContext getServletContext(ServletRequest servletRequest);

    ClassLoader getClassloader(ServletContext servletContext);

    String getServletContextName(ServletContext servletContext);

    String getContextPath(ServletContext servletContext);

    @Nullable
    Transaction createAndActivateTransaction(HttpServletRequest httpServletRequest);

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

    String getServletPath(HttpServletRequest servletRequest);

    String getPathInfo(HttpServletRequest servletRequest);

    Object getIncludeServletPathAttribute(HttpServletRequest servletRequest);

    Object getIncludePathInfoAttribute(HttpServletRequest servletRequest);

    boolean isInstanceOfHttpServlet(Object object);

    @Nullable
    Principal getUserPrincipal(HttpServletRequest httpServletRequest);

    @Nullable
    Object getAttribute(HttpServletRequest request, String attributeName);

    Collection<String> getHeaderNames(HttpServletResponse httpServletResponse);

    Collection<String> getHeaders(HttpServletResponse httpServletResponse, String headerName);

    Map<String, String[]> getParameterMap(HttpServletRequest httpServletRequest);

    boolean isCommitted(HttpServletResponse httpServletResponse);

    int getStatus(HttpServletResponse httpServletResponse);
}
