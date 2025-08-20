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


import co.elastic.apm.agent.tracer.TraceState;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.pooling.Recyclable;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Unfortunately, it can't implement {@link Recyclable} because {@link #releaseResources} method might not always be the
 * last method called, hence we don't have any reliable hook to trigger recycling.
 */
public class AsyncRequestProducerWrapper implements AsyncRequestProducer {

    private final ApacheHttpClient5AsyncHelper asyncClientHelper;
    private final AsyncRequestProducer delegate;

    @Nullable
    private TraceState<?> toPropagate;

    @Nullable
    private Span<?> span;

    AsyncRequestProducerWrapper(ApacheHttpClient5AsyncHelper helper,
                                AsyncRequestProducer delegate,
                                @Nullable Span<?> span,
                                TraceState<?> toPropagate) {
        this.asyncClientHelper = helper;
        this.delegate = delegate;
        this.span = span;
        this.toPropagate = toPropagate;
    }

    /**
     * Here we should catch {@link IllegalStateException} in cases
     * when {@link CloseableHttpAsyncClient#close()} executed.
     */
    @Override
    public void sendRequest(RequestChannel requestChannel, HttpContext httpContext) throws HttpException, IOException {
        RequestChannelWrapper wrappedRequestChannel = asyncClientHelper.wrapRequestChannel(requestChannel, span, toPropagate);
        boolean isNotNullWrappedRequestChannel = null != wrappedRequestChannel;
        try {
            delegate.sendRequest(isNotNullWrappedRequestChannel ? wrappedRequestChannel : requestChannel, httpContext);
        } catch (HttpException | IOException | IllegalStateException e) {
            // ensures that toPropagate reference count is properly decremented in case of an exception
            internalResetState();
            throw e;
        } finally {
            if (isNotNullWrappedRequestChannel) {
                asyncClientHelper.recycle(wrappedRequestChannel);
            }
        }
    }

    @Override
    public boolean isRepeatable() {
        return delegate.isRepeatable();
    }

    @Override
    public void failed(Exception e) {
        try {
            delegate.failed(e);
        } finally {
            internalResetState();
        }
    }

    @Override
    public int available() {
        return delegate.available();
    }

    @Override
    public void produce(DataStreamChannel dataStreamChannel) throws IOException {
        // this method might be called after releaseResources, hence preventing us from implementing recycling easily
        delegate.produce(dataStreamChannel);
    }

    @Override
    public void releaseResources() {
        delegate.releaseResources();
        internalResetState();
    }

    private void internalResetState(){
        span = null;
        if (toPropagate != null) {
            toPropagate.decrementReferences();
            toPropagate = null;
        }

    }

}
