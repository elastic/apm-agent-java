/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.grpc.v1_6_1.testapp;

import co.elastic.apm.agent.grpc.testapp.HelloClient;
import co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloGrpc;
import co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloReply;
import co.elastic.apm.agent.grpc.v1_6_1.testapp.generated.HelloRequest;
import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

class HelloClientImpl extends HelloClient<HelloRequest, HelloReply> {

    private final HelloGrpc.HelloBlockingStub blockingStub;
    private final HelloGrpc.HelloFutureStub futureStub;
    private final HelloGrpc.HelloStub stub;

    public HelloClientImpl(String host, int port) {
        this(ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext(true) // disable encryption to avoid self-signed certificates
            .build());
    }

    private HelloClientImpl(ManagedChannel channel) {
        super(channel);
        ClientInterceptor interceptor = getClientInterceptor();
        this.blockingStub = HelloGrpc.newBlockingStub(channel).withInterceptors(interceptor);
        this.futureStub = HelloGrpc.newFutureStub(channel).withInterceptors(interceptor);
        this.stub = HelloGrpc.newStub(channel).withInterceptors(interceptor);
    }

    @Override
    public HelloRequest buildRequest(String user, int depth) {
        HelloRequest.Builder request = HelloRequest.newBuilder()
            .setDepth(depth);

        if (user != null) {
            request.setUserName(user);
        }
        return request.build();
    }

    @Override
    public HelloReply executeBlocking(HelloRequest request) {
        return blockingStub
            .withDeadline(getDeadline())
            .sayHello(request);
    }

    @Override
    public ListenableFuture<HelloReply> executeAsync(HelloRequest request) {
        return futureStub
            .withDeadline(getDeadline())
            .sayHello(request);
    }


    @Override
    protected StreamObserver<HelloRequest> doSayManyHello(StreamObserver<HelloReply> responseObserver) {
        return stub
            .withDeadline(getDeadline())
            .sayManyHello(responseObserver);
    }

    @Override
    protected void doSayHelloMany(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        stub
            .withDeadline(getDeadline())
            .sayHelloMany(request, responseObserver);
    }

    @Override
    protected StreamObserver<HelloRequest> doSayHelloManyMany(StreamObserver<HelloReply> responseObserver) {
        return stub
            .withDeadline(getDeadline())
            .sayHelloStream(responseObserver);
    }

    @Override
    public String getResponseMessage(HelloReply reply) {
        return reply.getMessage();
    }

}
