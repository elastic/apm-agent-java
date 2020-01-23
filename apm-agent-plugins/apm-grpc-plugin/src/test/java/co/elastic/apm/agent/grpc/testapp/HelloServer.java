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
import co.elastic.apm.agent.grpc.testapp.HelloReply;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;

public class HelloServer {

    private static final Logger logger = LoggerFactory.getLogger(HelloServer.class);

    @Nullable
    private Server server;

    public HelloServer() {
    }

    public void start(int port) throws IOException {
        logger.info("starting grpc server on port {}", port);
        HelloGrpcImpl serverImpl = new HelloGrpcImpl();

        server = ServerBuilder.forPort(port)
            .addService(serverImpl)
            .build()
            .start();
    }

    public void stop() throws InterruptedException {
        logger.info("stopping grpc server");
        if (null != server) {
            server.shutdown().awaitTermination();
        }
        logger.info("grpc server shutdown complete");
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (null != server) {
            server.awaitTermination();
        }
        logger.info("grpc server shutdown complete");
    }

    // service implementation
    static class HelloGrpcImpl extends HelloGrpc.HelloImplBase {
        @Override
        public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {

            String userName = request.getUserName();

            if (userName.isEmpty()) {
                // this seems to be the preferred way to deal with errors on server implementation
                responseObserver.onError(new StatusRuntimeException(Status.INVALID_ARGUMENT));
                return;
            }

            HelloReply reply = HelloReply.newBuilder()
                .setMessage(String.format("Hello %s", userName))
                .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }
}
