/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.es.restclient;

import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.objectpool.Recyclable;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;

import javax.annotation.Nullable;

public class ResponseListenerWrapper implements ResponseListener, Recyclable {

    private ElasticsearchRestClientInstrumentationHelperImpl helper;
    @Nullable
    private ResponseListener delegate;
    @Nullable
    private Span span;

    ResponseListenerWrapper(ElasticsearchRestClientInstrumentationHelperImpl helper) {
        this.helper = helper;
    }

    ResponseListenerWrapper with(ResponseListener delegate, Span span) {
        this.delegate = delegate;
        this.span = span;
        return this;
    }

    @Override
    public void onSuccess(Response response) {
        try {
            if (span != null) {
                helper.finishClientSpan(response, span, null);
            }
        } finally {
            if (delegate != null) {
                delegate.onSuccess(response);
            }
            helper.recycle(this);
        }
    }

    @Override
    public void onFailure(Exception exception) {
        try {
            if (span != null) {
                helper.finishClientSpan(null, span, exception);
            }
        } finally {
            if (delegate != null) {
                delegate.onFailure(exception);
            }
            helper.recycle(this);
        }
    }

    @Override
    public void resetState() {
        delegate = null;
        span = null;
    }
}
