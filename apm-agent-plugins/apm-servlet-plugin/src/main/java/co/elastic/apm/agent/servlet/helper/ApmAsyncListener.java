/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.agent.servlet.helper;

import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.servlet.ServletTransactionHelper;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * Based on brave.servlet.ServletRuntime$TracingAsyncListener (under Apache license 2.0)
 */
public class ApmAsyncListener implements AsyncListener {
    private final ServletTransactionHelper servletTransactionHelper;
    private final Transaction transaction;
    private volatile boolean completed = false;

    ApmAsyncListener(ServletTransactionHelper servletTransactionHelper, Transaction transaction) {
        this.servletTransactionHelper = servletTransactionHelper;
        this.transaction = transaction;
    }

    @Override
    public void onComplete(AsyncEvent event) {
        if (!completed) {
            endTransaction(event);
            completed = true;
        }
    }

    @Override
    public void onTimeout(AsyncEvent event) {
        if (!completed) {
            endTransaction(event);
            completed = true;
        }
    }

    @Override
    public void onError(AsyncEvent event) {
        if (!completed) {
            endTransaction(event);
            completed = true;
        }
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
        HttpServletRequest request = (HttpServletRequest) event.getSuppliedRequest();
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
        servletTransactionHelper.onAfter(transaction, event.getThrowable(),
            response.isCommitted(), response.getStatus(), request.getMethod(), parameterMap,
            request.getServletPath(), request.getPathInfo(), contentTypeHeader);
    }
}
