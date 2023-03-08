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
package co.elastic.apm.agent.esrestclient;

import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.pooling.Recyclable;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;

import javax.annotation.Nullable;

public class ResponseListenerWrapper implements ResponseListener, Recyclable {

    private final ElasticsearchRestClientInstrumentationHelper helper;
    private final Tracer tracer;

    @Nullable
    private ResponseListener delegate;

    /**
     * When {@literal true}, the context object is a client span that needs to be ended on completion, when {@literal false}
     * it means that only context-propagation (aka activation/deactivation) is required.
     */
    private boolean isClientSpan;

    @Nullable
    private volatile AbstractSpan<?> context;

    ResponseListenerWrapper(ElasticsearchRestClientInstrumentationHelper helper, Tracer tracer) {
        this.helper = helper;
        this.tracer = tracer;
    }

    ResponseListenerWrapper withClientSpan(ResponseListener delegate, Span<?> span) {
        // Order is important due to visibility - write to span last on this (initiating) thread
        this.delegate = delegate;
        this.isClientSpan = true;
        this.context = span;
        return this;
    }

    ResponseListenerWrapper withContextPropagation(ResponseListener delegate, AbstractSpan<?> context) {
        // Order is important due to visibility - write to span last on this (initiating) thread
        this.delegate = delegate;
        this.isClientSpan = false;
        this.context = context;
        return this;
    }

    @Override
    public void onSuccess(Response response) {
        if (isClientSpan) {
            onSuccessClient(response);
        } else {
            onSuccessContextPropagation(response);
        }
    }

    private void onSuccessContextPropagation(Response response) {
        AbstractSpan<?> localContext = context;
        boolean activate = localContext != null && tracer.getActive() != localContext;
        try {
            if (activate) {
                localContext.activate();
            }
            if (delegate != null) {
                delegate.onSuccess(response);
            }
        } finally {
            if (activate) {
                localContext.deactivate();
            }
            helper.recycle(this);
        }
    }

    private void onSuccessClient(Response response){
        try {
            finishClientSpan(response, null);
        } finally {
            if (delegate != null) {
                delegate.onSuccess(response);
            }
            helper.recycle(this);
        }
    }

    @Override
    public void onFailure(Exception exception) {
        if (isClientSpan) {
            onFailureClient(exception);
        } else {
            onFailureContextPropagation(exception);
        }
    }

    private void onFailureClient(Exception exception) {
        try {
            finishClientSpan(null, exception);
        } finally {
            if (delegate != null) {
                delegate.onFailure(exception);
            }
            helper.recycle(this);
        }
    }

    private void onFailureContextPropagation(Exception exception) {
        AbstractSpan<?> localContext = context;
        boolean activate = localContext != null && tracer.getActive() != localContext;
        try {
            if (activate) {
                localContext.activate();
            }
            if (delegate != null) {
                delegate.onFailure(exception);
            }
        } finally {
            if (activate) {
                localContext.deactivate();
            }
            helper.recycle(this);
        }
    }

    private void finishClientSpan(@Nullable Response response, @Nullable Throwable throwable) {
        // First read volatile span to ensure visibility on executing thread
        AbstractSpan<?> localSpan = context;
        if (localSpan instanceof Span<?>) {
            helper.finishClientSpan(response, (Span<?>) localSpan, throwable);
        }
    }

    @Override
    public void resetState() {
        delegate = null;
        context = null;
        isClientSpan = false;
    }
}
