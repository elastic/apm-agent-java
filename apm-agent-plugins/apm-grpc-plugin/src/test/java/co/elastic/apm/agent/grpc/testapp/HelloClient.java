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
package co.elastic.apm.agent.grpc.testapp;

import co.elastic.apm.agent.grpc.testapp.HelloGrpc;
import co.elastic.apm.agent.grpc.testapp.HelloRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class HelloClient {

    private static final Logger logger = LoggerFactory.getLogger(GrpcApp.class);

    private final ManagedChannel channel;
    private final HelloGrpc.HelloBlockingStub blockingStub;

    public HelloClient(String host, int port) {
        this(ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext() // disable encryption to avoid self-signed certificates
            .build());
    }

    private HelloClient(ManagedChannel channel){
        this.channel = channel;
        this.blockingStub = HelloGrpc.newBlockingStub(channel);
    }

    public Optional<String> sayHello(String user) {
        HelloRequest.Builder request = HelloRequest.newBuilder();
        if (user != null) {
            request.setUserName(user);
        }
        HelloReply reply;
        try {
            reply = blockingStub.sayHello(request.build());
        } catch (StatusRuntimeException e) {
            logger.error("server error {} {}", e.getStatus(), e.getMessage());
            return Optional.empty();
        }
        return Optional.of(reply.getMessage());
    }

    public void stop() throws InterruptedException {
        channel.shutdown()
            .awaitTermination(1, TimeUnit.SECONDS);
    }
}
