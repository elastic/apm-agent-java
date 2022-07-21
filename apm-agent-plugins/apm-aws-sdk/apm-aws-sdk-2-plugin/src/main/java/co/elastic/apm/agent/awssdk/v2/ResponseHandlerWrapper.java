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
package co.elastic.apm.agent.awssdk.v2;

import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import org.reactivestreams.Publisher;
import software.amazon.awssdk.core.Response;
import software.amazon.awssdk.core.internal.http.TransformingAsyncResponseHandler;
import software.amazon.awssdk.http.SdkHttpResponse;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class ResponseHandlerWrapper<T> implements TransformingAsyncResponseHandler<Response<T>> {

    private final TransformingAsyncResponseHandler<Response<T>> delegate;
    private final Span span;

    public ResponseHandlerWrapper(TransformingAsyncResponseHandler<Response<T>> delegate, Span span) {
        this.delegate = delegate;
        this.span = span;
    }

    @Override
    public CompletableFuture<Response<T>> prepare() {
        CompletableFuture<Response<T>> delegateFuture = delegate.prepare();
        delegateFuture.whenComplete((r, t) -> {
            if (t != null) {
                span.captureException(t);
                span.withOutcome(Outcome.FAILURE);
            } else if (r.exception() != null) {
                span.captureException(r.exception());
                span.withOutcome(Outcome.FAILURE);
            } else {
                span.withOutcome(Outcome.SUCCESS);
            }
            span.end();
        });

        return delegateFuture;
    }

    @Override
    public void onHeaders(SdkHttpResponse sdkHttpResponse) {
        delegate.onHeaders(sdkHttpResponse);
    }

    @Override
    public void onStream(Publisher<ByteBuffer> publisher) {
        delegate.onStream(publisher);
    }

    @Override
    public void onError(Throwable throwable) {
        if (!span.isFinished()) {
            span.captureException(throwable);
            span.withOutcome(Outcome.FAILURE);
        }

        delegate.onError(throwable);
    }
}
