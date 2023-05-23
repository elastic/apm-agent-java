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

import javax.annotation.Nullable;

public abstract class IAwsSdkDataSource<R, C> {
    public static final String BUCKET_NAME_FIELD = "Bucket";
    public static final String OBJECT_KEY_FIELD = "Key";
    public static final String OBJECT_DESTINATION_KEY_FIELD = "DestinationKey";
    public static final String COPY_SOURCE_FIELD = "CopySource";
    public static final String TABLE_NAME_FIELD = "TableName";
    public static final String KEY_CONDITION_EXPRESSION_FIELD = "KeyConditionExpression";
    public static final String QUEUE_NAME_FIELD = "QueueName";
    public static final String QUEUE_NAME_URL_FIELD = "QueueUrl";

    @Nullable
    public abstract String getOperationName(R sdkRequest, C context);

    @Nullable
    public abstract String getRegion(R sdkRequest, C context);

    @Nullable
    public abstract String getFieldValue(String fieldName, R sdkRequest);

    @Nullable
    public String getQueueNameFromQueueUrl(@Nullable String queueUrl) {
        if (queueUrl != null) {
            int lastSlashIdx = queueUrl.lastIndexOf('/');
            if (lastSlashIdx < queueUrl.length()) {
                return queueUrl.substring(lastSlashIdx + 1);
            }
        }
        return null;
    }
}
