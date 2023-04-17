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

import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;

import javax.annotation.Nullable;
import java.net.URI;

public abstract class AbstractS3InstrumentationHelper<R, C> extends AbstractAwsSdkInstrumentationHelper<R, C> {
    public static final String S3_TYPE = "s3";

    protected AbstractS3InstrumentationHelper(Tracer tracer, IAwsSdkDataSource<R, C> awsSdkDataSource) {
        super(tracer, awsSdkDataSource);
    }


    @Nullable
    public Span<?> startSpan(R request, URI httpURI, C context) {
        AbstractSpan<?> active = tracer.getActive();
        if (active == null) {
            return null;
        }
        Span<?> span = active.createExitSpan();
        if (span == null) {
            return null;
        }
        String operationName = awsSdkDataSource.getOperationName(request, context);
        String bucketName = awsSdkDataSource.getFieldValue(IAwsSdkDataSource.BUCKET_NAME_FIELD, request);

        span.withType("storage")
            .withSubtype(S3_TYPE)
            .withAction(operationName);
        StringBuilder name = span.getAndOverrideName(AbstractSpan.PRIORITY_DEFAULT);
        if (name != null) {
            name.append("S3 ").append(operationName);
            if (bucketName != null && !bucketName.isEmpty()) {
                name.append(" ").append(bucketName);
            }
        }
        span.withName("S3", AbstractSpan.PRIORITY_DEFAULT - 1);
        setDestinationContext(span, httpURI, request, context, S3_TYPE, bucketName);

        return span;
    }
}
