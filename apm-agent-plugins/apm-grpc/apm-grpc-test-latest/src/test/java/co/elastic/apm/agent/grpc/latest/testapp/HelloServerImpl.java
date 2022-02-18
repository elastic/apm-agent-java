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
package co.elastic.apm.agent.grpc.latest.testapp;

import co.elastic.apm.agent.grpc.latest.testapp.generated.HelloGrpc;
import co.elastic.apm.agent.grpc.latest.testapp.generated.HelloReply;
import co.elastic.apm.agent.grpc.latest.testapp.generated.HelloRequest;
import co.elastic.apm.agent.grpc.testapp.HelloClient;
import co.elastic.apm.agent.grpc.testapp.HelloServer;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.atomic.AtomicReference;

class HelloServerImpl extends HelloServer<HelloRequest, HelloReply> {

    public HelloServerImpl(int port, HelloClient<HelloRequest, HelloReply> client) {
        super(port, client);
    }

    @Override
    protected BindableService getService() {
        return new HelloGrpcImpl(client, syncBarriers);
    }


    private static class HelloGrpcImpl extends HelloGrpc.HelloImplBase {

        private final GenericHelloGrpcImpl<HelloRequest, HelloReply> genericServer;

        HelloGrpcImpl(HelloClient<HelloRequest, HelloReply> client, AtomicReference<Sync> syncBarriers) {
            this.genericServer = new GenericHelloGrpcImpl<>(client, syncBarriers);
        }

        @Override
        public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
            genericServer.doSayHello(
                request,
                responseObserver,
                HelloRequest::getUserName,
                HelloRequest::getDepth,
                m -> HelloReply.newBuilder().setMessage(m).build());
        }

        @Override
        public StreamObserver<HelloRequest> sayManyHello(StreamObserver<HelloReply> responseObserver) {
            return genericServer.doSayManyHello(
                responseObserver,
                HelloRequest::getUserName,
                HelloRequest::getDepth,
                m -> HelloReply.newBuilder().setMessage(m).build());
        }

        @Override
        public void sayHelloMany(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
            genericServer.doSayHelloMany(
                request,
                responseObserver,
                HelloRequest::getUserName,
                HelloRequest::getDepth,
                (m) -> HelloReply.newBuilder().setMessage(m).build()
            );
        }

        @Override
        public StreamObserver<HelloRequest> sayHelloStream(StreamObserver<HelloReply> responseObserver) {
            return genericServer.doSayHelloStream(
                responseObserver,
                HelloRequest::getUserName,
                HelloRequest::getDepth,
                m -> HelloReply.newBuilder().setMessage(m).build());
        }
    }

}
