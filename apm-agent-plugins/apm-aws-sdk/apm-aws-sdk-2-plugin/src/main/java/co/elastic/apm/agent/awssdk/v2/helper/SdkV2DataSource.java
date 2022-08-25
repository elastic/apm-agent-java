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
package co.elastic.apm.agent.awssdk.v2.helper;

import co.elastic.apm.agent.awssdk.common.IAwsSdkDataSource;
import software.amazon.awssdk.auth.signer.AwsSignerExecutionAttribute;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.http.ExecutionContext;
import software.amazon.awssdk.regions.Region;

import javax.annotation.Nullable;

public class SdkV2DataSource extends IAwsSdkDataSource<SdkRequest, ExecutionContext> {

    private static final SdkV2DataSource INSTANCE = new SdkV2DataSource();

    public static SdkV2DataSource getInstance() {
        return INSTANCE;
    }

    @Override
    @Nullable
    public String getOperationName(SdkRequest sdkRequest, ExecutionContext context) {
        return context.executionAttributes().getAttribute(AwsSignerExecutionAttribute.OPERATION_NAME);
    }

    @Override
    @Nullable
    public String getRegion(SdkRequest sdkRequest, ExecutionContext context) {
        Region region = context.executionAttributes().getAttribute(AwsSignerExecutionAttribute.SIGNING_REGION);
        if (region != null) {
            return region.id();
        }
        return null;
    }

    @Override
    @Nullable
    public String getFieldValue(String fieldName, SdkRequest sdkRequest) {
        String value = sdkRequest.getValueForField(fieldName, String.class).orElse(null);
        if (QUEUE_NAME_FIELD.equals(fieldName) && value == null) {
            value = getQueueNameFromQueueUrl(getFieldValue(QUEUE_NAME_URL_FIELD, sdkRequest));
        }
        return value;
    }
}
