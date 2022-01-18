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
package co.elastic.apm.agent.grpc.testapp;

import co.elastic.apm.agent.util.ExecutorUtils;
import io.grpc.BindableService;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public abstract class HelloServer<Req,Rep> {

    private static final Logger logger = LoggerFactory.getLogger(HelloServer.class);

    public static final int POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;

    private static boolean verbose = true;

    protected final int port;
    protected final AtomicReference<Sync> syncBarriers;
    protected final HelloClient<Req, Rep>  client;

    // contains listener method name that should throw an exception
    protected final AtomicReference<String> listenerExceptionMethod;
    private Server server;
    private ExecutorService serverPool;

    protected static class Sync {
        public CyclicBarrier processingStart;
        public CyclicBarrier processingEnd;
    }

    protected HelloServer(int port, HelloClient<Req, Rep> client) {
        this.port = port;
        this.syncBarriers = new AtomicReference<>();
        this.client = client;
        this.listenerExceptionMethod = new AtomicReference<>();
    }

    public void setListenerExceptionMethod(String methodName) {
        listenerExceptionMethod.set(methodName);
    }

    protected abstract BindableService getService();

    protected ServerInterceptor getInterceptor() {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                return new TestServerListener<ReqT>(next.startCall(call, headers), listenerExceptionMethod);
            }
        };
    }

    public static void setVerbose(boolean value){
        verbose = value;
    }

    public void start() throws IOException {
        serverPool = Executors.newFixedThreadPool(POOL_SIZE, new ThreadFactory() {
            int i = 0;

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName(String.format("grpc-server-%d", i++));
                thread.setDaemon(true);
                return thread;
            }
        });
        server = ServerBuilder.forPort(port)
            .addService(getService())
            .executor(serverPool)
            .intercept(getInterceptor())
            .build();

        logger.info("starting grpc server on port {}", port);

        try {
            server.start();
            logger.info("grpc server start complete on port {}", port);
        } catch (IOException e) {
            logger.error("grpc server unable to start on port {}", port, e);
        }
    }



    public void stop() throws InterruptedException {
        logger.info("stopping grpc server on port {}", port);
        Sync sync = syncBarriers.get();
        if (sync != null) {
            checkNoWaiting(sync.processingStart, true);
            checkNoWaiting(sync.processingEnd, false);
        }
        boolean shutdownOk = server.shutdown().awaitTermination(1, TimeUnit.SECONDS);
        if (!shutdownOk) {
            throw new IllegalStateException("something is wrong, unable to properly shut down server");
        }

        ExecutorUtils.shutdownAndWaitTermination(serverPool, 1, TimeUnit.SECONDS);

        logger.info("grpc server shutdown complete on port {}", port);
    }

    protected static void checkNoWaiting(CyclicBarrier barrier, boolean isStart) {
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


    protected static class GenericHelloGrpcImpl<Req,Rep> {
        private final HelloClient<Req, Rep> client;
        private final AtomicReference<Sync> syncBarriers;
        private final boolean verbose;

        public GenericHelloGrpcImpl(HelloClient<Req, Rep> client, AtomicReference<Sync> syncBarriers) {
            this.client = client;
            this.syncBarriers = syncBarriers;
            this.verbose = HelloServer.verbose;
        }

        public interface ReplyHandler {
            void gracefulError();
            void sendReply(String msg);
        }

        public void doSayHello(Req request,
                               StreamObserver<Rep> responseObserver,
                               Function<Req, String> getName,
                               Function<Req, Integer> getDepth,
                               Function<String, Rep> buildStreamingResponse) {

            String userName = getName.apply(request);
            int depth = getDepth.apply(request);
            ReplyHandler replyHandler = new ReplyHandler() {
                @Override
                public void gracefulError() {
                    responseObserver.onError(Status.INVALID_ARGUMENT.asRuntimeException());
                }

                @Override
                public void sendReply(String msg) {
                    responseObserver.onNext(buildStreamingResponse.apply(msg));
                    responseObserver.onCompleted();
                }
            };

            String message;

            // in case client cancels the call
            // - if server side processing isn't started, we will not capture any transaction
            // - while it's being processed
            // we need to be sure that the created transaction (if any) will have the 'cancelled' status
            // even if the server has already started processing it.
            syncWait(true);

           logVerbose("start processing");

            if (depth > 0) {
                int nextDepth = depth - 1;
                String nestedResult = client.sayHello(userName, nextDepth);
                if (nestedResult == null) {
                    nestedResult = String.format("error(%d)", nextDepth);
                }
                message = String.format("nested(%d)->%s", depth, nestedResult);
            } else {

                if (userName.isEmpty()) {
                    logVerbose("trigger a graceful error");
                    // this seems to be the preferred way to deal with errors on server implementation
                    replyHandler.gracefulError();
                    return;
                } else if ("boom".equals(userName)) {
                    logVerbose("trigger a server exception aka 'not so graceful error'");
                    // this will be translated into a Status#UNKNOWN
                    throw new RuntimeException("boom");
                }

                message = String.format("hello(%s)", userName);
            }

            logVerbose("end of processing, response not sent yet");

            // end of processing, but before sending response
            syncWait(false);

            logVerbose("start sending response");

            replyHandler.sendReply(message);

            logVerbose("end of sending response");

        }

        private void logVerbose(String msg, Object... args){
            if (verbose) {
                logger.info(msg, args);
            }
        }

        public StreamObserver<Req> doSayManyHello(StreamObserver<Rep> responseObserver,
                                                  Function<Req, String> getName,
                                                  Function<Req, Integer> getDepth,
                                                  Function<String, Rep> buildStreamingResponse) {

            return new StreamObserver<>() {

                List<String> names = new ArrayList<>();
                Integer depth = 0;

                @Override
                public void onNext(Req helloRequest) {
                    names.add(getName.apply(helloRequest));
                    depth = getDepth.apply(helloRequest);
                }

                @Override
                public void onError(Throwable throwable) {
                    responseObserver.onError(throwable);
                }

                @Override
                public void onCompleted() {
                    String msg = String.format("hello to [%s] %d times", String.join(",", names), depth);
                    responseObserver.onNext(buildStreamingResponse.apply(msg));
                    responseObserver.onCompleted();
                }

            };
        }

        public void doSayHelloMany(Req request,
                                   StreamObserver<Rep> responseObserver,
                                   Function<Req, String> getName,
                                   Function<Req, Integer> getDepth,
                                   Function<String, Rep> buildStreamingResponse) {

            for (int i = 0; i < getDepth.apply(request); i++) {
                responseObserver.onNext(buildStreamingResponse.apply(getName.apply(request)));
            }
            responseObserver.onCompleted();
        }

        public StreamObserver<Req> doSayHelloStream(StreamObserver<Rep> responseObserver,
                                                  Function<Req, String> getName,
                                                  Function<Req, Integer> getDepth,
                                                  Function<String, Rep> buildStreamingResponse) {

            return new StreamObserver<>() {

                @Override
                public void onNext(Req helloRequest) {
                    String name = getName.apply(helloRequest);
                    int count = getDepth.apply(helloRequest);

                    if (count <= 0) {
                        responseObserver.onCompleted();
                    } else {
                        for (int i = 0; i < count; i++) {
                            responseObserver.onNext(buildStreamingResponse.apply(String.format("hello(%s)", name)));
                        }
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    responseObserver.onError(throwable);
                }

                @Override
                public void onCompleted() {
                    // client terminated call, just ignore for now
                }

            };
        }

        private void syncWait(boolean isStart) {
            Sync sync = syncBarriers.get();
            if (sync != null) {
                String step = isStart ? "start" : "end";
                logVerbose("server waiting sync on " + step);
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
                logVerbose("waited for {} ms at processing {}", waitedMillis, step);
            }
        }
    }

}
