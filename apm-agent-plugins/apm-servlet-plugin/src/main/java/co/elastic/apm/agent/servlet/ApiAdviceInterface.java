package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.transaction.Transaction;

import java.util.Enumeration;

public interface ApiAdviceInterface<ServletRequest, HttpServletRequest> {


    Transaction getTransactionAttribute(ServletRequest servletRequest);

    boolean isHttpServletRequest(ServletRequest servletRequest);

    HttpServletRequest getHttpServletRequest(ServletRequest servletRequest);

    boolean isRequestDispatcherType(ServletRequest servletRequest);

    abstract boolean isAsyncDispatcherType(ServletRequest servletRequest);

    abstract boolean isForwardDispatcherType(ServletRequest servletRequest);

    abstract boolean isIncludeDispatcherType(ServletRequest servletRequest);

    abstract boolean isErrorDispatcherType(ServletRequest servletRequest);

    abstract ServletContext getServletContext(ServletRequest servletRequest);

    abstract ClassLoader getClassloader(ServletContext servletContext);

    abstract String getServletContextName(ServletContext servletContext);

    abstract String getContextPath(ServletContext servletContext);

    abstract Transaction createAndActivateTransaction(HttpServletRequest httpServletRequest);

    abstract void handleCookies(Request request, HttpServletRequest httpServletRequest);

    abstract Enumeration<String> getHeaderNames(HttpServletRequest httpServletRequest);

    abstract String getHeader(HttpServletRequest httpServletRequest, String name);

    abstract String getProtocol(HttpServletRequest httpServletRequest);

    abstract String getMethod(HttpServletRequest httpServletRequest);

    abstract boolean isSecure(HttpServletRequest servletRequest);

    abstract String getScheme(HttpServletRequest servletRequest);

    abstract String getServerName(HttpServletRequest servletRequest);

    abstract int getServerPort(HttpServletRequest servletRequest);

    abstract String getRequestURI(HttpServletRequest servletRequest);

    abstract String getQueryString(HttpServletRequest servletRequest);

    abstract String getRemoteAddr(HttpServletRequest servletRequest);

    abstract String getServletPath(HttpServletRequest servletRequest);

    abstract String getPathInfo(HttpServletRequest servletRequest);

    abstract Object getIncludeServletPathAttribute(HttpServletRequest servletRequest);

    abstract Object getIncludePathInfoAttribute(HttpServletRequest servletRequest);
}
