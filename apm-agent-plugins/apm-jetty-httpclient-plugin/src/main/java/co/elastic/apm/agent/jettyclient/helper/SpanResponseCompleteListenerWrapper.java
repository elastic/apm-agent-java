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
package co.elastic.apm.agent.jettyclient.helper;

import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;

public class SpanResponseCompleteListenerWrapper implements Response.CompleteListener {

    private final Span span;

    public SpanResponseCompleteListenerWrapper(Span span) {
        this.span = span;
    }

    @Override
    public void onComplete(Result result) {
        if (span != null) {
            try {
                Response response = result.getResponse();
                Throwable t = result.getFailure();
                if (response != null) {
                    span.getContext().getHttp().withStatusCode(response.getStatus());
                }
                if (t != null) {
                    span.withOutcome(Outcome.FAILURE);
                }
                span.captureException(t);
            } finally {
                span.end();
            }
        }
    }
}
