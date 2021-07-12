package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.servlet.helper.JavaxServletTransactionCreationHelper;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import java.util.Enumeration;

import static co.elastic.apm.agent.servlet.ServletTransactionHelper.TRANSACTION_ATTRIBUTE;

public class JavaxServletApiAdvice extends BaseServletApiAdvice<ServletRequest, HttpServletRequest, ServletContext> {

    private static JavaxServletTransactionCreationHelper transactionCreationHelper;

    static {
        transactionCreationHelper = new JavaxServletTransactionCreationHelper(GlobalTracer.requireTracerImpl());
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnterServletService(@Advice.Argument(0) ServletRequest servletRequest) {
        return onServletEnter(servletRequest);
    }

    @Override
    Transaction getTransactionAttribute(ServletRequest servletRequest) {
        return (Transaction) servletRequest.getAttribute(TRANSACTION_ATTRIBUTE);
    }

    @Override
    boolean isHttpServletRequest(ServletRequest servletRequest) {
        return servletRequest instanceof HttpServletRequest;
    }

    @Override
    HttpServletRequest getHttpServletRequest(ServletRequest servletRequest) {
        return (HttpServletRequest) servletRequest;
    }

    @Override
    boolean isRequestDispatcherType(ServletRequest servletRequest) {
        return servletRequest.getDispatcherType() == DispatcherType.REQUEST;
    }

    @Override
    boolean isAsyncDispatcherType(ServletRequest servletRequest) {
        return servletRequest.getDispatcherType() == DispatcherType.ASYNC;
    }

    @Override
    boolean isForwardDispatcherType(ServletRequest servletRequest) {
        return servletRequest.getDispatcherType() == DispatcherType.FORWARD;
    }

    @Override
    boolean isIncludeDispatcherType(ServletRequest servletRequest) {
        return servletRequest.getDispatcherType() == DispatcherType.INCLUDE;
    }

    @Override
    boolean isErrorDispatcherType(ServletRequest servletRequest) {
        return servletRequest.getDispatcherType() == DispatcherType.ERROR;
    }

    @Override
    ClassLoader getClassloader(ServletContext servletContext) {
        return transactionCreationHelper.getClassloader(servletContext);
    }

    @Override
    String getServletContextName(ServletContext servletContext) {
        return servletContext.getServletContextName();
    }

    @Override
    String getContextPath(ServletContext servletContext) {
        return servletContext.getContextPath();
    }

    @Override
    Transaction createAndActivateTransaction(HttpServletRequest httpServletRequest) {
        return transactionCreationHelper.createAndActivateTransaction(httpServletRequest);
    }

    @Override
    void handleCookies(Request request, HttpServletRequest servletRequest) {
        if (servletRequest.getCookies() != null) {
            for (Cookie cookie : servletRequest.getCookies()) {
                request.addCookie(cookie.getName(), cookie.getValue());
            }
        }
    }

    @Override
    Enumeration<String> getHeaderNames(HttpServletRequest servletRequest) {
        return servletRequest.getHeaderNames();
    }

    @Override
    String getHeader(HttpServletRequest servletRequest, String name) {
        return servletRequest.getHeader(name);
    }

    @Override
    String getProtocol(HttpServletRequest servletRequest) {
        return servletRequest.getProtocol();
    }

    @Override
    String getMethod(HttpServletRequest servletRequest) {
        return servletRequest.getMethod();
    }

    @Override
    boolean isSecure(HttpServletRequest servletRequest) {
        return servletRequest.isSecure();
    }

    @Override
    String getScheme(HttpServletRequest servletRequest) {
        return servletRequest.getScheme();
    }

    @Override
    String getServerName(HttpServletRequest servletRequest) {
        return servletRequest.getServerName();
    }

    @Override
    int getServerPort(HttpServletRequest servletRequest) {
        return servletRequest.getServerPort();
    }

    @Override
    String getRequestURI(HttpServletRequest servletRequest) {
        return servletRequest.getRequestURI();
    }

    @Override
    String getQueryString(HttpServletRequest servletRequest) {
        return servletRequest.getQueryString();
    }

    @Override
    String getRemoteAddr(HttpServletRequest servletRequest) {
        return servletRequest.getRemoteAddr();
    }

    @Override
    String getServletPath(HttpServletRequest servletRequest) {
        return servletRequest.getServletPath();
    }

    @Override
    String getPathInfo(HttpServletRequest servletRequest) {
        return servletRequest.getPathInfo();
    }

    @Override
    Object getIncludeServletPathAttribute(HttpServletRequest servletRequest) {
        return servletRequest.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
    }

    @Override
    Object getIncludePathInfoAttribute(HttpServletRequest servletRequest) {
        return servletRequest.getAttribute(RequestDispatcher.INCLUDE_PATH_INFO);
    }

    @Override
    ServletContext getServletContext(ServletRequest servletRequest) {
        return servletRequest.getServletContext();
    }
}
