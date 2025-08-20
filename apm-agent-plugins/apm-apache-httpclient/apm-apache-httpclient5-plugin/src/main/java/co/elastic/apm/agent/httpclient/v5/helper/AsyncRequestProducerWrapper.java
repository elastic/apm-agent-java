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

public class AsyncRequestProducerWrapper implements AsyncRequestProducer, Recyclable {

    private final ApacheHttpClient5AsyncHelper asyncClientHelper;
    private volatile AsyncRequestProducer delegate;

    @Nullable
    private TraceState<?> toPropagate;

    @Nullable
    private Span<?> span;

    AsyncRequestProducerWrapper(ApacheHttpClient5AsyncHelper helper) {
        this.asyncClientHelper = helper;
    }

    public AsyncRequestProducerWrapper with(AsyncRequestProducer delegate, @Nullable Span<?> span,
                                            TraceState<?> toPropagate) {
        this.span = span;
        toPropagate.incrementReferences();
        this.toPropagate = toPropagate;
        // write to volatile field last
        this.delegate = delegate;
        return this;
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
            asyncClientHelper.recycle(this);
            throw e;
        } finally {
            if (isNotNullWrappedRequestChannel) {
                asyncClientHelper.recycle(wrappedRequestChannel);
            }
        }
    }

    @Override
    public boolean isRepeatable() {
        return delegate != null && delegate.isRepeatable();
    }

    @Override
    public void failed(Exception e) {
        if (delegate != null) {
            delegate.failed(e);
        }
    }

    @Override
    public int available() {
        return delegate != null ? delegate.available() : 0;
    }

    @Override
    public void produce(DataStreamChannel dataStreamChannel) throws IOException {
        if (delegate != null) {
            delegate.produce(dataStreamChannel);
        }
    }

    @Override
    public void releaseResources() {
        if (delegate != null) {
            delegate.releaseResources();
            asyncClientHelper.recycle(this);
        }
    }

    @Override
    public void resetState() {
        span = null;
        if (toPropagate != null) {
            toPropagate.decrementReferences();
            toPropagate = null;
        }
        // write to volatile field last
        delegate = null;
    }

}
