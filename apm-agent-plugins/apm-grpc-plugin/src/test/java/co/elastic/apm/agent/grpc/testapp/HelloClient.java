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

import co.elastic.apm.agent.grpc.testapp.generated.HelloGrpc;
import co.elastic.apm.agent.grpc.testapp.generated.HelloReply;
import co.elastic.apm.agent.grpc.testapp.generated.HelloRequest;
import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class HelloClient {

    private static final Logger logger = LoggerFactory.getLogger(GrpcApp.class);

    private final ManagedChannel channel;
    private final HelloGrpc.HelloBlockingStub blockingStub;
    private final HelloGrpc.HelloFutureStub futureStub;

    public HelloClient(String host, int port) {
        this(ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext() // disable encryption to avoid self-signed certificates
            .build());
    }

    private HelloClient(ManagedChannel channel) {
        this.channel = channel;
        this.blockingStub = HelloGrpc.newBlockingStub(channel).withInterceptors();
        this.futureStub = HelloGrpc.newFutureStub(channel);
    }

    public String sayHello(String user, int depth) {
        HelloRequest request = buildRequest(user, depth);
        HelloReply reply;
        try {
            reply = blockingStub.sayHello(request);
        } catch (StatusRuntimeException e) {
            logger.error("server error {} {}", e.getStatus(), e.getMessage());
            return null;
        }
        return reply.getMessage();
    }

    public Future<String> saysHelloAsync(String user, int depth) {
        HelloRequest request = buildRequest(user, depth);
        ListenableFuture<HelloReply> future = futureStub.sayHello(request);
        return new Future<String>(){

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return future.cancel(mayInterruptIfRunning);
            }

            @Override
            public boolean isCancelled() {
                return future.isCancelled();
            }

            @Override
            public boolean isDone() {
                return future.isDone();
            }

            @Override
            public String get() throws InterruptedException, ExecutionException {
                // TODO : check if something is thrown when there is a server error
                return future.get().getMessage();
            }

            @Override
            public String get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                // TODO : check if something is thrown when there is a server error
                return future.get(timeout, unit).getMessage();
            }
        };

    }

    public void stop() throws InterruptedException {
        boolean doShutdown = !channel.isShutdown();
        if (doShutdown) {
            channel.shutdown()
                .awaitTermination(1, TimeUnit.SECONDS);
        }

        if (!channel.isTerminated()) {
            logger.warn("channel has been shut down with running calls");
        }

        channel.awaitTermination(1, TimeUnit.SECONDS);
        logger.info("client channel has been properly shut down");
    }

    private static HelloRequest buildRequest(String user, int depth) {
        HelloRequest.Builder request = HelloRequest.newBuilder()
            .setDepth(depth);

        if (user != null) {
            request.setUserName(user);
        }
        return request.build();
    }
}
