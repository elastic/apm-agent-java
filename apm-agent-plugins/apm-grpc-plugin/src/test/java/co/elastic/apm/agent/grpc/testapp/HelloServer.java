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
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class HelloServer {

    private static final Logger logger = LoggerFactory.getLogger(HelloServer.class);

    private final int port;
    private final Server server;
    private final AtomicReference<Sync> syncBarriers;

    private static class Sync {
        CyclicBarrier processingStart;
        CyclicBarrier processingEnd;
    }

    public HelloServer(int port) {
        this.port = port;
        this.syncBarriers = new AtomicReference<>();
        HelloClient nestedClient = new HelloClient("localhost", port);
        HelloGrpcImpl serverImpl = new HelloGrpcImpl(nestedClient);
        this.server = ServerBuilder.forPort(port)
            .addService(serverImpl)
            .build();

    }

    public void start() throws IOException {
        logger.info("starting grpc server on port {}", port);
        server.start();
        logger.info("grpc server start complete");
    }

    public void stop() throws InterruptedException {
        logger.info("stopping grpc server");
        Sync sync = syncBarriers.get();
        if(sync!=null){
            checkNoWaiting(sync.processingStart, true);
            checkNoWaiting(sync.processingEnd, true);
        }
        boolean shutdownOk = server.shutdown().awaitTermination(1, TimeUnit.SECONDS);
        if (!shutdownOk) {
            throw new IllegalStateException("something is wrong, unable to properly shut down server");
        }
        logger.info("grpc server shutdown complete");
    }

    private static void checkNoWaiting(CyclicBarrier barrier, boolean isStart) {
        if (barrier.getNumberWaiting() > 0) {
            String msg = String.format("server still waiting for sync, someone likely forgot to release the %s barrier", isStart ? "start" : "end");
            throw new IllegalArgumentException(msg);
        }

    }

    public void useBarriersForProcessing(CyclicBarrier start, CyclicBarrier end) {
        Sync sync = new Sync();
        sync.processingStart = start;
        sync.processingEnd = end;
        this.syncBarriers.set(sync);
    }

    // service implementation
    private class HelloGrpcImpl extends HelloGrpc.HelloImplBase {

        private final HelloClient client;

        HelloGrpcImpl(HelloClient client) {
            this.client = client;
        }

        @Override
        public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {

            String userName = request.getUserName();
            int depth = request.getDepth();
            String message;

            // in case client cancels the call
            // - if server side processing isn't started, we will not capture any transaction
            // - while it's being processed
            // we need to be sure that the created transaction (if any) will have the 'cancelled' status
            // even if the server has already started processing it.
            syncWait(true);

            logger.info("start processing");

            if (depth > 0) {
                int nextDepth = depth - 1;
                String nestedResult = client.sayHello(userName, nextDepth);
                if (nestedResult == null) {
                    nestedResult = String.format("error(%d)", nextDepth);
                }
                message = String.format("nested(%d)->%s", depth, nestedResult);
            } else {

                if (userName.isEmpty()) {
                    logger.info("trigger a graceful error");
                    // this seems to be the preferred way to deal with errors on server implementation
                    responseObserver.onError(Status.INVALID_ARGUMENT.asRuntimeException());
                    return;
                } else if ("boom".equals(userName)) {
                    logger.info("trigger a server exception aka 'not so graceful error'");
                    // this will be translated into a Status#UNKNOWN
                    throw new RuntimeException("boom");
                }

                message = String.format("hello(%s)", userName);
            }

            logger.info("end of processing, response not sent yet");

            // end of processing, but before sending response
            syncWait(false);

            logger.info("start sending response");

            HelloReply reply = HelloReply.newBuilder()
                .setMessage(message)
                .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();

            logger.info("end of sending response");

        }

        private void syncWait(boolean isStart) {
            Sync sync = syncBarriers.get();
            if (sync != null) {
                String step = isStart ? "start" : "end";
                logger.info("server waiting sync on " + step);
                CyclicBarrier barrier = isStart ? sync.processingStart : sync.processingEnd;
                long waitStart = System.currentTimeMillis();
                try {
                    barrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    throw new RuntimeException(e);
                } finally {
                    barrier.reset();
                }
                long waitedMillis = System.currentTimeMillis() - waitStart;
                logger.info("waited for {} ms at processing {}", waitedMillis, step);
            }
        }


    }
}
