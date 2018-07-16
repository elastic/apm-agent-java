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
package co.elastic.apm.servlet.helper;

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.servlet.AsyncInstrumentation;
import co.elastic.apm.servlet.ServletApiAdvice;
import co.elastic.apm.servlet.ServletTransactionHelper;

import javax.servlet.AsyncContext;
import javax.servlet.ServletRequest;

public class StartAsyncAdviceHelperImpl implements AsyncInstrumentation.StartAsyncAdviceHelper<AsyncContext> {

    private static final String ASYNC_LISTENER_ADDED = ServletApiAdvice.class.getName() + ".asyncListenerAdded";

    private final ServletTransactionHelper servletTransactionHelper;
    private final ElasticApmTracer tracer;

    public StartAsyncAdviceHelperImpl(ElasticApmTracer tracer) {
        this.tracer = tracer;
        servletTransactionHelper = new ServletTransactionHelper(tracer);
    }

    @Override
    public void onExitStartAsync(AsyncContext asyncContext) {
        final ServletRequest request = asyncContext.getRequest();
        if (request.getAttribute(ASYNC_LISTENER_ADDED) != null) {
            return;
        }
        if (tracer.currentTransaction() != null &&
            request.getAttribute(ASYNC_LISTENER_ADDED) == null) {
            // makes sure that the listener is only added once, even if the request is wrapped
            // which leads to multiple invocations of startAsync for the same underlying request
            request.setAttribute(ASYNC_LISTENER_ADDED, Boolean.TRUE);
            // specifying the request and response is important
            // otherwise AsyncEvent.getSuppliedRequest returns null per spec
            // however, only some application server like WebSphere actually implement it that way
            asyncContext.addListener(new ApmAsyncListener(servletTransactionHelper, tracer.currentTransaction()),
                asyncContext.getRequest(), asyncContext.getResponse());
        }
    }
}
