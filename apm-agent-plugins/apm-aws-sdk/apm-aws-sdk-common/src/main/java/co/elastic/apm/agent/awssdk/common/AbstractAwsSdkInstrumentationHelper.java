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
package co.elastic.apm.agent.awssdk.common;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;

import javax.annotation.Nullable;
import java.net.URI;

public abstract class AbstractAwsSdkInstrumentationHelper<R, C> {
    protected final IAwsSdkDataSource<R, C> awsSdkDataSource;
    protected final ElasticApmTracer tracer;

    protected AbstractAwsSdkInstrumentationHelper(ElasticApmTracer tracer, IAwsSdkDataSource<R, C> awsSdkDataSource) {
        this.tracer = tracer;
        this.awsSdkDataSource = awsSdkDataSource;
    }

    public ElasticApmTracer getTracer() {
        return tracer;
    }

    @Nullable
    public abstract Span startSpan(R request, URI httpURI, C context);

    protected void setDestinationContext(Span span, @Nullable URI uri, R sdkRequest, C context, String type, @Nullable String name) {
        if (uri != null) {
            span.getContext().getDestination()
                .withAddress(uri.getHost())
                .withPort(uri.getPort());
        }

        span.getContext().getServiceTarget()
            .withType(type)
            .withName(name)
            .withNameOnlyDestinationResource();

        span.getContext().getDestination()
            .getCloud()
            .withRegion(awsSdkDataSource.getRegion(sdkRequest, context));
    }
}
