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
package co.elastic.apm.agent.httpclient.v4.helper;

import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.pooling.Recyclable;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;

import javax.annotation.Nullable;

class FutureCallbackWrapper<T> implements FutureCallback<T>, Recyclable {
    private final ApacheHttpAsyncClientHelper helper;
    @Nullable
    private FutureCallback<T> delegate;
    @Nullable
    private HttpContext context;
    private volatile Span<?> span;

    FutureCallbackWrapper(ApacheHttpAsyncClientHelper helper) {
        this.helper = helper;
    }

    FutureCallbackWrapper<T> with(@Nullable FutureCallback<T> delegate, @Nullable HttpContext context, Span<?> span) {
        this.delegate = delegate;
        this.context = context;
        // write to volatile field last
        this.span = span;
        return this;
    }

    @Override
    public void completed(T result) {
        try {
            finishSpan(null);
        } finally {
            if (delegate != null) {
                delegate.completed(result);
            }
            helper.recycle(this);
        }
    }

    @Override
    public void failed(Exception ex) {
        try {
            finishSpan(ex);
        } finally {
            if (delegate != null) {
                delegate.failed(ex);
            }
            helper.recycle(this);
        }
    }

    @Override
    public void cancelled() {
        try {
            finishSpan(null);
        } finally {
            if (delegate != null) {
                delegate.cancelled();
            }
            helper.recycle(this);
        }
    }

    private void finishSpan(@Nullable Exception e) {
        // start by reading the volatile field
        final Span<?> localSpan = span;
        try {
            if (context != null) {
                Object responseObject = context.getAttribute(HttpCoreContext.HTTP_RESPONSE);
                if (responseObject instanceof HttpResponse) {
                    StatusLine statusLine = ((HttpResponse) responseObject).getStatusLine();
                    if (statusLine != null) {
                        span.getContext().getHttp().withStatusCode(statusLine.getStatusCode());
                    }
                }
            }
            localSpan.captureException(e);

            if (e != null) {
                localSpan.withOutcome(Outcome.FAILURE);
            }
        } finally {
            localSpan.end();
        }
    }

    @Override
    public void resetState() {
        delegate = null;
        context = null;
        // write to volatile field last
        span = null;
    }
}
