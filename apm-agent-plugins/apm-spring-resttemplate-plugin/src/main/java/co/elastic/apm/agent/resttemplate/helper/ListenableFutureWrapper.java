/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.resttemplate.helper;

import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.objectpool.Recyclable;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.concurrent.FailureCallback;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.util.concurrent.SuccessCallback;

import javax.annotation.Nullable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ListenableFutureWrapper<T> implements ListenableFuture<T>, Recyclable {
    private final RestTemplateInstrumentationHelperImpl helper;
    @Nullable
    private ListenableFuture<T> delegate;
    private volatile Span span;

    public ListenableFutureWrapper(RestTemplateInstrumentationHelperImpl helper) {
        this.helper = helper;
    }

    ListenableFutureWrapper<T> with(@Nullable ListenableFuture<T> delegate, Span span) {
        this.delegate = delegate;
        this.span = span;
        return this;
    }

    @Override
    public void addCallback(ListenableFutureCallback<? super T> listenableFutureCallback) {
        if (delegate != null) {
            delegate.addCallback(listenableFutureCallback);
        }
    }

    @Override
    public void addCallback(SuccessCallback<? super T> successCallback, FailureCallback failureCallback) {
        if (delegate != null) {
            delegate.addCallback(successCallback, failureCallback);
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (delegate != null) {
            return delegate.cancel(mayInterruptIfRunning);
        }
        return false;
    }

    @Override
    public boolean isCancelled() {
        if (delegate != null) {
            return delegate.isCancelled();
        }
        return false;
    }

    @Override
    public boolean isDone() {
        if (delegate != null) {
            return delegate.isDone();
        }
        return false;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        T t = null;
        Throwable methodThrowable  = null;
        try {
            if (delegate != null) {
                t = delegate.get();
            }
        } catch (Throwable e) {
            methodThrowable = e;
            throw e;
        } finally {
            finishSpan(t, span, methodThrowable);
            helper.recycle(this);
        }
        return t;
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        T t = null;
        Throwable methodThrowable = null;
        try {
            if (delegate != null) {
                t = delegate.get(timeout, unit);
            }
        } catch (Throwable e) {
            methodThrowable = e;
            throw e;
        } finally {
            finishSpan(t, span, methodThrowable);
            helper.recycle(this);
        }
        return t;
    }

    @Override
    public void resetState() {
        delegate = null;
        span = null;
    }

    private void finishSpan(T t, Span span, @Nullable Throwable methodThrowable) {
        try {
            if (t instanceof ClientHttpResponse) {
                span.getContext().getHttp().withStatusCode(((ClientHttpResponse) t).getRawStatusCode());
            }
            span.captureException(methodThrowable);
        } catch (Throwable th) {
            span.captureException(th);
        } finally {
            span.end();
        }
    }
}
