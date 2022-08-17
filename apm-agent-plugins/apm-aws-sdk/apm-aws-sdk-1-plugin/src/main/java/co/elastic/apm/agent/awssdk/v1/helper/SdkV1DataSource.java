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
package co.elastic.apm.agent.awssdk.v1.helper;

import co.elastic.apm.agent.awssdk.common.IAwsSdkDataSource;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.handlers.HandlerContextKey;
import com.amazonaws.http.ExecutionContext;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;

import javax.annotation.Nullable;

public class SdkV1DataSource extends IAwsSdkDataSource<Request<?>, ExecutionContext> {
    @Nullable
    private static final SdkV1DataSource INSTANCE = new SdkV1DataSource();

    public static SdkV1DataSource getInstance() {
        return INSTANCE;
    }

    private final WeakMap<Object, String> requestLookupMap = WeakConcurrent.buildMap();

    @Override
    @Nullable
    public String getOperationName(Request<?> request, ExecutionContext context) {
        return request.getHandlerContext(HandlerContextKey.OPERATION_NAME);
    }

    @Override
    @Nullable
    public String getRegion(Request<?> request, ExecutionContext context) {
        return request.getHandlerContext(HandlerContextKey.SIGNING_REGION);
    }

    @Override
    @Nullable
    public String getFieldValue(String fieldName, Request<?> request) {
        AmazonWebServiceRequest amazonWebServiceRequest = request.getOriginalRequest();
        if (IAwsSdkDataSource.BUCKET_NAME_FIELD.equals(fieldName) ||
            IAwsSdkDataSource.TABLE_NAME_FIELD.equals(fieldName) ||
            IAwsSdkDataSource.QUEUE_NAME_FIELD.equals(fieldName)) {
            return requestLookupMap.get(amazonWebServiceRequest);
        } else if (IAwsSdkDataSource.KEY_CONDITION_EXPRESSION_FIELD.equals(fieldName)
            && amazonWebServiceRequest instanceof QueryRequest) {
            return ((QueryRequest) amazonWebServiceRequest).getKeyConditionExpression();
        }

        return null;
    }

    public void putLookupValue(Object key, @Nullable String stringValue) {
        if (stringValue != null && !requestLookupMap.containsKey(key)) {
            requestLookupMap.put(key, stringValue);
        }
    }

    public void removeLookupValue(Object key) {
        requestLookupMap.remove(key);
    }
}
