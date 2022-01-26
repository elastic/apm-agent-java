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
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map;

public class JavaxServletApiAdvice extends ServletApiAdvice implements ServletHelper<ServletRequest, ServletResponse, HttpServletRequest, HttpServletResponse, ServletContext> {

    private static JavaxServletTransactionCreationHelper transactionCreationHelper;
    private static JavaxServletApiAdvice helper;
    static {
        transactionCreationHelper = new JavaxServletTransactionCreationHelper(GlobalTracer.requireTracerImpl());
        helper = new JavaxServletApiAdvice();
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnterServletService(@Advice.Argument(0) ServletRequest servletRequest,
                                               @Advice.This @Nullable Object thisRef) {
        try {
            return onServletEnter(servletRequest, helper);
        } catch (NoClassDefFoundError e) {

            debugContextClassLoader();
            debugClass(JavaxServletApiAdvice.class);

            if (thisRef != null) {
                debugClass(thisRef.getClass());
            }
            throw e;
        }
    }


    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExitServletService(@Advice.Argument(0) ServletRequest servletRequest,
                                            @Advice.Argument(1) ServletResponse servletResponse,
                                            @Advice.Enter @Nullable Object transactionOrScopeOrSpan,
                                            @Advice.Thrown @Nullable Throwable t,
                                            @Advice.This Object thiz) {
        onExitServlet(servletRequest, servletResponse, transactionOrScopeOrSpan, t, thiz, helper);
    }

    @Override
    public boolean isHttpServletRequest(ServletRequest servletRequest) {
        return servletRequest instanceof HttpServletRequest;
    }

    @Override
    public boolean isHttpServletResponse(ServletResponse servletResponse) {
        return servletResponse instanceof HttpServletResponse;
    }

    private static void debugClass(Class<?> type){
        ClassLoader cl = type.getClassLoader();
        if (cl != null) {
            System.out.printf("classloader of class '%s' : %s (%s)\n", type.getName(), cl.getName(), cl);
            tryLoadClass(cl, "javax.servlet.http.HttpServletRequest");
        }

    }

    private static void debugContextClassLoader(){
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        System.out.printf("context classloader : %s \n", cl);
        if (cl != null) {
            tryLoadClass(cl,"javax.servlet.http.HttpServletRequest");
        }
    }

    private static void tryLoadClass(ClassLoader cl, String className) {
        try {
            Class<?> aClass = Class.forName(className, false, cl);
            System.out.printf("classloader '%s' can access class '%s'", cl, className);
            System.out.printf("classloader of '%s' : ", aClass.getClassLoader());
        } catch (ClassNotFoundException e) {
            System.out.printf("classloader '%s' can not access class '%s'", cl, className);
        }
    }


    @Override
    public boolean isRequestDispatcherType(ServletRequest servletRequest) {
        return servletRequest.getDispatcherType() == DispatcherType.REQUEST;
    }

    @Override
    public boolean isAsyncDispatcherType(ServletRequest servletRequest) {
        return servletRequest.getDispatcherType() == DispatcherType.ASYNC;
    }

    @Override
    public boolean isForwardDispatcherType(ServletRequest servletRequest) {
        return servletRequest.getDispatcherType() == DispatcherType.FORWARD;
    }

    @Override
    public boolean isIncludeDispatcherType(ServletRequest servletRequest) {
        return servletRequest.getDispatcherType() == DispatcherType.INCLUDE;
    }

    @Override
    public boolean isErrorDispatcherType(ServletRequest servletRequest) {
        return servletRequest.getDispatcherType() == DispatcherType.ERROR;
    }

    @Override
    public ClassLoader getClassloader(ServletContext servletContext) {
        return transactionCreationHelper.getClassloader(servletContext);
    }

    @Override
    public String getServletContextName(ServletContext servletContext) {
        return servletContext.getServletContextName();
    }

    @Override
    public String getContextPath(ServletContext servletContext) {
        return servletContext.getContextPath();
    }

    @Override
    public Transaction createAndActivateTransaction(HttpServletRequest httpServletRequest) {
        return transactionCreationHelper.createAndActivateTransaction(httpServletRequest);
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
    public Principal getUserPrincipal(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getUserPrincipal();
    }

    @Nullable
    @Override
    public Object getAttribute(ServletRequest servletRequest, String attributeName) {
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
    public ServletContext getServletContext(ServletRequest servletRequest) {
        return servletRequest.getServletContext();
    }
}
