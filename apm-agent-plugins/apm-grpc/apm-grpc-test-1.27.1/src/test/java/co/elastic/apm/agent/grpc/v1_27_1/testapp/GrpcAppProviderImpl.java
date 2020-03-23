/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.grpc.v1_27_1.testapp;

import co.elastic.apm.agent.grpc.testapp.GrpcApp;
import co.elastic.apm.agent.grpc.testapp.GrpcAppProvider;
import co.elastic.apm.agent.grpc.testapp.HelloClient;
import co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloReply;
import co.elastic.apm.agent.grpc.v1_27_1.testapp.generated.HelloRequest;

public class GrpcAppProviderImpl implements GrpcAppProvider {

    public static final GrpcAppProviderImpl INSTANCE = new GrpcAppProviderImpl();

    public GrpcApp getGrpcApp(String host, int port) {
        HelloClient<HelloRequest, HelloReply> client = new HelloClientImpl(host, port);
        HelloServerImpl server = new HelloServerImpl(port, client);
        return new GrpcApp(server, client);
    }

}
