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
package co.elastic.apm.agent.servlet.helper;

import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.servlet.ServletTransactionHelper;

import javax.annotation.Nullable;

import co.elastic.apm.agent.tracer.metadata.Response;
import co.elastic.apm.agent.tracer.pooling.Recyclable;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static co.elastic.apm.agent.servlet.ServletTransactionHelper.TRANSACTION_ATTRIBUTE;

/**
 * Based on brave.servlet.ServletRuntime$TracingAsyncListener (under Apache license 2.0)
 *
 * onComplete is always called, even if onError/onTimeout is called, as per the specifications.
 * However, when onError/onTimeout is called, the Response that can be obtained through the event arg is not yet set with the right
 * status code, for that we need to rely on onComplete. On the the other hand, the event arg that is received in onComplete does not
 * contain the Throwable that comes with the event in the preceding onError, so we need to keep it.
 *
 * After testing on Payara, WildFly, Tomcat, WebSphere Liberty and Jetty, here is a summary of subtle differences:
 *  - Liberty is the only one that will invoke onError following an AsyncListener.start invocation with a Runnable that ends with Exception
 *  - WildFly will not resume the Response until timed-out in the same scenario, but it invokes onTimeout, which is good for our tests
 *  - Jetty on the same scenario will just go crazy endlessly trying to run the Runnable over and over
 *  - Some containers may release the response after onError/onTimeout to return to the client, meaning that onComplete is called afterwards
 *  - Jetty fails to invoke onError after AsyncContext.dispatch to a Servlet that ends with ServletException
 *  - JBoss EAP 6.4 does not invoke onComplete after onError is invoked
 */
public class JakartaApmAsyncListener implements AsyncListener, Recyclable {

    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final JakartaAsyncContextAdviceHelper asyncContextAdviceHelperImpl;
    private final ServletTransactionHelper servletTransactionHelper;
    @Nullable
    private volatile Transaction<?> transaction;
    @Nullable
    private volatile Throwable throwable;

    JakartaApmAsyncListener(JakartaAsyncContextAdviceHelper asyncContextAdviceHelperImpl) {
        this.asyncContextAdviceHelperImpl = asyncContextAdviceHelperImpl;
        this.servletTransactionHelper = asyncContextAdviceHelperImpl.getServletTransactionHelper();
    }

    JakartaApmAsyncListener withTransaction(Transaction<?> transaction) {
        this.transaction = transaction;
        return this;
    }

    @Override
    public void onComplete(AsyncEvent event) {
        endTransaction(event);
    }

    @Override
    public void onTimeout(AsyncEvent event) {
        throwable = event.getThrowable();
        if (isJBossEap6(event)) {
            endTransaction(event);
        }
        /*
            NOTE: HTTP status code may not have been set yet, so we do not call endTransaction() from here.

            According to the Servlet 3 specification
            (http://download.oracle.com/otn-pub/jcp/servlet-3.0-fr-eval-oth-JSpec/servlet-3_0-final-spec.pdf, section 2.3.3.3),
            onComplete() should always be called by the container even in the case of timeout or error, and the final
            HTTP status code should be set by then. So we'll just defer to onComplete() for finalizing the span and do
            nothing here.

            But JBoss EAP 6 is a special one...
        */
    }

    @Override
    public void onError(AsyncEvent event) {
        throwable = event.getThrowable();
        if (isJBossEap6(event)) {
            endTransaction(event);
        }
        /*
            NOTE: HTTP status code may not have been set yet, so we only hold a reference to the related error that may not be
            otherwise available, but not calling endTransaction() from here.

            According to the Servlet 3 specification
            (http://download.oracle.com/otn-pub/jcp/servlet-3.0-fr-eval-oth-JSpec/servlet-3_0-final-spec.pdf, section 2.3.3.3),
            onComplete() should always be called by the container even in the case of timeout or error, and the final
            HTTP status code should be set by then. So we'll just defer to onComplete() for finalizing the span and do
            nothing here.

            But JBoss EAP 6 is a special one...
        */
    }

    private boolean isJBossEap6(AsyncEvent event) {
        final ServletContext context = event.getSuppliedRequest().getServletContext();
        return context.getMajorVersion() == 3 && context.getMinorVersion() == 0 && System.getProperty("jboss.home.dir") != null;
    }

    /**
     * If another async is created (ex via asyncContext.dispatch), this needs to be re-attached
     */
    @Override
    public void onStartAsync(AsyncEvent event) {
        AsyncContext eventAsyncContext = event.getAsyncContext();
        if (eventAsyncContext != null) {
            eventAsyncContext.addListener(this, event.getSuppliedRequest(), event.getSuppliedResponse());
        }
    }

    // unfortunately, the duplication can't be avoided,
    // because only the onExitServletService method may contain references to the servlet API
    // (see class-level Javadoc)
    private void endTransaction(AsyncEvent event) {
        // To ensure transaction is ended only by a single event
        if (completed.getAndSet(true) || transaction == null) {
            return;
        }

        try {
            HttpServletRequest request = (HttpServletRequest) event.getSuppliedRequest();
            request.removeAttribute(TRANSACTION_ATTRIBUTE);

            HttpServletResponse response = (HttpServletResponse) event.getSuppliedResponse();
            final Response resp = transaction.getContext().getResponse();
            if (transaction.isSampled() && servletTransactionHelper.isCaptureHeaders()) {
                for (String headerName : response.getHeaderNames()) {
                    resp.addHeader(headerName, response.getHeaders(headerName));
                }
            }
            // request.getParameterMap() may allocate a new map, depending on the servlet container implementation
            // so only call this method if necessary
            final String contentTypeHeader = request.getHeader("Content-Type");
            final Map<String, String[]> parameterMap;
            if (transaction.isSampled() && servletTransactionHelper.captureParameters(request.getMethod(), contentTypeHeader)) {
                parameterMap = request.getParameterMap();
            } else {
                parameterMap = null;
            }
            Throwable throwableToSend = event.getThrowable();
            if (throwableToSend == null) {
                throwableToSend = throwable;
            }
            servletTransactionHelper.onAfter(transaction, throwableToSend,
                response.isCommitted(), response.getStatus(), true, request.getMethod(), parameterMap,
                request.getServletPath(), request.getPathInfo(), contentTypeHeader, false
            );
        } finally {
            asyncContextAdviceHelperImpl.recycle(this);
        }
    }

    @Override
    public void resetState() {
        transaction = null;
        throwable = null;
        completed.set(false);
    }
}
