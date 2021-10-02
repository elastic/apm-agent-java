
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
package co.elastic.apm.agent.r2dbc.helper;

import co.elastic.apm.agent.sdk.state.GlobalState;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;


@GlobalState
public class R2dbcGlobalState {

    public static final WeakMap<Object, Object[]> statementConnectionMap = WeakConcurrent.buildMap();
    public static final WeakMap<Object, Object[]> batchConnectionMap = WeakConcurrent.buildMap();
    public static final WeakMap<Connection, ConnectionMetaData> r2dbcMetaDataMap = WeakConcurrent.buildMap();
    public static final WeakMap<ConnectionFactory, ConnectionFactoryOptions> connectionFactoryMap = WeakConcurrent.buildMap();
    public static final WeakMap<Connection, ConnectionFactoryOptions> connectionOptionsMap = WeakConcurrent.buildMap();
    public static final WeakMap<Class<?>, Boolean> metadataSupported = WeakConcurrent.buildMap();

}
