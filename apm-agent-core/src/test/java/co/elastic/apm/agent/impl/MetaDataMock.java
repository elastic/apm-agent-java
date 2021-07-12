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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.impl.payload.CloudProviderInfo;
import co.elastic.apm.agent.impl.payload.ProcessInfo;
import co.elastic.apm.agent.impl.payload.Service;
import co.elastic.apm.agent.impl.payload.SystemInfo;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.Future;

public class MetaDataMock {

    /**
     * Creates a metadata mock based on the given info synchronously
     *
     * @return a mock future, already containing the medata info
     */
    public static Future<MetaData> create(ProcessInfo process, Service service, SystemInfo system, @Nullable CloudProviderInfo cloudProviderInfo,
                                          Map<String, String> globalLabels) {
        return new MetaData.NoWaitFuture(new MetaData(process, service, system, cloudProviderInfo, globalLabels));
    }
}
