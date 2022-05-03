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

import co.elastic.apm.agent.awssdk.common.AbstractDynamoDBInstrumentationHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import com.amazonaws.Request;
import com.amazonaws.http.ExecutionContext;

import javax.annotation.Nullable;

public class DynamoDbHelper extends AbstractDynamoDBInstrumentationHelper<Request<?>, ExecutionContext> {

    static final WeakMap<Object, String> dynamoDbRequestToTableNameMap = WeakConcurrent.buildMap();

    @Nullable
    private static DynamoDbHelper INSTANCE;

    public static DynamoDbHelper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DynamoDbHelper(GlobalTracer.requireTracerImpl());
        }
        return INSTANCE;
    }


    public DynamoDbHelper(ElasticApmTracer tracer) {
        super(tracer, SdkV1DataSource.getInstance());
    }

    public void putTableName(Object key, String tableName) {
        dynamoDbRequestToTableNameMap.put(key, tableName);
    }

    @Nullable
    public String getTableName(Object key) {
        return dynamoDbRequestToTableNameMap.get(key);
    }

    public void removeTableNameForKey(Object key) {
        dynamoDbRequestToTableNameMap.remove(key);
    }

    public boolean hasTableNameForKey(Object key) {
        return dynamoDbRequestToTableNameMap.containsKey(key);
    }
}
