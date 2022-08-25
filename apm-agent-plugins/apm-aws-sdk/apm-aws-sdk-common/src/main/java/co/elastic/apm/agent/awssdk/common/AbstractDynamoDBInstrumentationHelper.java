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
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;

import javax.annotation.Nullable;
import java.net.URI;

public abstract class AbstractDynamoDBInstrumentationHelper<R, C> extends AbstractAwsSdkInstrumentationHelper<R, C> {
    public static final String DYNAMO_DB_TYPE = "dynamodb";

    protected AbstractDynamoDBInstrumentationHelper(ElasticApmTracer tracer, IAwsSdkDataSource<R, C> awsSdkDataSource) {
        super(tracer, awsSdkDataSource);
    }

    public void enrichSpan(Span span, R sdkRequest, URI httpURI, C context) {
        String operationName = awsSdkDataSource.getOperationName(sdkRequest, context);
        String region = awsSdkDataSource.getRegion(sdkRequest, context);
        String tableName = awsSdkDataSource.getFieldValue(IAwsSdkDataSource.TABLE_NAME_FIELD, sdkRequest);

        span.withType("db")
            .withSubtype(DYNAMO_DB_TYPE)
            .withAction("query");

        span.getContext().getDb().withInstance(region).withType(DYNAMO_DB_TYPE);


        if ("Query".equals(operationName)) {
            span.getContext().getDb().withStatement(awsSdkDataSource.getFieldValue(IAwsSdkDataSource.KEY_CONDITION_EXPRESSION_FIELD, sdkRequest));
        }


        StringBuilder name = span.getAndOverrideName(AbstractSpan.PRIO_DEFAULT);
        if (name != null) {
            name.append("DynamoDB ").append(operationName);

            if (tableName != null) {
                name.append(" ").append(tableName);
            }
        }

        setDestinationContext(span, httpURI, sdkRequest, context, DYNAMO_DB_TYPE, null);
    }

    @Nullable
    public Span startSpan(R sdkRequest, URI httpURI, C context) {
        Span span = tracer.createExitChildSpan();
        if (span == null) {
            return null;
        }

        enrichSpan(span, sdkRequest, httpURI, context);

        return span;
    }
}
