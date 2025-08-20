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
package co.elastic.apm.agent.httpclient.v5.helper;


import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.pooling.Recyclable;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;

import javax.annotation.Nullable;

public class FutureCallbackWrapper<T> implements FutureCallback<T>, Recyclable {
    private final ApacheHttpClient5AsyncHelper helper;
    @Nullable
    private FutureCallback<T> delegate;
    @Nullable
    private HttpContext context;
    private volatile Span<?> span;

    FutureCallbackWrapper(ApacheHttpClient5AsyncHelper helper) {
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
            try {
                if (delegate != null) {
                    delegate.completed(result);
                }
            } finally {
                helper.recycle(this);
            }
        }
    }

    @Override
    public void failed(Exception ex) {
        try {
            finishSpan(ex);
        } finally {
            try {
                if (delegate != null) {
                    delegate.failed(ex);
                }
            } finally {
                helper.recycle(this);
            }
        }
    }

    public void failedWithoutExecution(Throwable ex) {
        try {
            final Span<?> localSpan = span;
            localSpan.captureException(ex).end();
        } finally {
            helper.recycle(this);
        }
    }

    @Override
    public void cancelled() {
        try {
            finishSpan(null);
        } finally {
            try {
                if (delegate != null) {
                    delegate.cancelled();
                }
            } finally {
                helper.recycle(this);
            }
        }
    }

    private void finishSpan(@Nullable Exception e) {
        // start by reading the volatile field
        final Span<?> localSpan = span;
        try {
            if (context instanceof HttpCoreContext) {
                HttpResponse response = ((HttpCoreContext) context).getResponse();
                if (response != null) {
                    span.getContext().getHttp().withStatusCode(response.getCode());
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
