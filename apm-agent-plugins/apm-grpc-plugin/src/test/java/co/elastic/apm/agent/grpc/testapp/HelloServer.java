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
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class HelloServer {

    private static final Logger logger = LoggerFactory.getLogger(HelloServer.class);

    private final int port;
    private final Server server;

    public HelloServer(int port) {
        this.port = port;
        HelloClient nestedClient = new HelloClient("localhost", port);
        HelloGrpcImpl serverImpl = new HelloGrpcImpl(nestedClient);
        this.server = ServerBuilder.forPort(port)
            .addService(serverImpl)
//            .intercept(new GrpcUnaryServerInterceptor())
            .build();

    }

    public void start() throws IOException {
        logger.info("starting grpc server on port {}", port);
        server.start();
        logger.info("grpc server start complete");
    }

    public void stop() throws InterruptedException {
        logger.info("stopping grpc server");
        server.shutdown().awaitTermination();
        logger.info("grpc server shutdown complete");
    }

    // service implementation
    private static class HelloGrpcImpl extends HelloGrpc.HelloImplBase {

        private final HelloClient client;

        HelloGrpcImpl(HelloClient client) {
            this.client = client;
        }

        @Override
        public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {

            String userName = request.getUserName();
            int depth = request.getDepth();
            String message;

            if (depth > 0) {
                int nextDepth = depth - 1;
                String nestedResult = client.sayHello(userName, nextDepth).orElse(String.format("error(%d)", nextDepth));
                message = String.format("nested(%d)->%s", depth, nestedResult);
            } else {

                if (userName.isEmpty()) {
                    // this seems to be the preferred way to deal with errors on server implementation
                    responseObserver.onError(new StatusRuntimeException(Status.INVALID_ARGUMENT));
                    return;
                } else if ("boom".equals(userName)) {
                    // this will be translated into a Status#UNKNOWN
                    throw new RuntimeException("boom");
                }

                message = String.format("hello(%s)", userName);
            }
            HelloReply reply = HelloReply.newBuilder()
                .setMessage(message)
                .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();

        }
    }
}
