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

import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public abstract class HelloClient<Req, Rep> {

    private static final Logger logger = LoggerFactory.getLogger(HelloClient.class);
    private final ManagedChannel channel;
    private final AtomicLong errorCount;
    private final AtomicReference<String> exeptionMethod;

    protected HelloClient(ManagedChannel channel) {
        this.channel = channel;
        this.errorCount = new AtomicLong(0);
        this.exeptionMethod = new AtomicReference<>();
    }

    protected static Deadline getDeadline() {
        return Deadline.after(10, TimeUnit.SECONDS);
    }

    public abstract Req buildRequest(String user, int depth);

    public abstract Rep executeBlocking(Req request);

    public abstract ListenableFuture<Rep> executeAsync(Req request);

    public abstract String getResponseMessage(Rep response);

    public long getErrorCount() {
        return errorCount.get();
    }

    public void setExceptionMethod(String method) {
        exeptionMethod.set(method);
    }

    protected ClientInterceptor getClientInterceptor() {
        return new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
                return new TestClientCallImpl<ReqT, RespT>(next.newCall(method, callOptions), exeptionMethod);
            }
        };
    }

    /**
     * Synchronous (blocking) hello
     *
     * @param user  user name
     * @param depth depth of nested calls, {@literal 0} for simple calls, use positive value for nesting
     * @return an hello statement
     */
    public final String sayHello(String user, int depth) {
        Req request = buildRequest(user, depth);
        Rep reply;
        try {
            reply = executeBlocking(request);
        } catch (StatusRuntimeException e) {
            logger.error("server error {} {}", e.getStatus(), e.getMessage());
            errorCount.incrementAndGet();
            return null;
        }
        return getResponseMessage(reply);
    }

    /**
     * Asynchronous hello
     *
     * @param user  user name
     * @param depth depth of nested calls, {@literal 0} for simple calls, use positive value for nesting
     * @return an hello statement
     */
    public final Future<String> saysHelloAsync(String user, int depth) {
        Req request = buildRequest(user, depth);
        ListenableFuture<Rep> future = executeAsync(request);
        return new Future<>() {

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
                return getResponseMessage(future.get());
            }

            @Override
            public String get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                // TODO : check if something is thrown when there is a server error
                return getResponseMessage(future.get(timeout, unit));
            }
        };

    }

    /**
     * Client streaming hello
     *
     * @param users list of users to say hello to
     * @param depth number of hello that should be said
     * @return an hello statement
     */
    public String sayManyHello(List<String> users, int depth) {

        List<Req> requests = users.stream()
            .map(u -> buildRequest(u, depth))
            .collect(Collectors.toList());

        AtomicReference<Rep> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        StreamObserver<Rep> responseObserver = new StreamObserver<>() {

            @Override
            public void onNext(Rep reply) {
                result.set(reply);
            }

            @Override
            public void onError(Throwable throwable) {
                errorCount.incrementAndGet();
                throw new IllegalStateException("unexpected error", throwable);
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        };
        StreamObserver<Req> requestObserver = doSayManyHello(responseObserver);
        for (Req request : requests) {
            requestObserver.onNext(request);
        }
        requestObserver.onCompleted();

        awaitLatch(latch);

        return getResponseMessage(result.get());
    }

    protected abstract StreamObserver<Req> doSayManyHello(StreamObserver<Rep> responseObserver);


    /**
     * Server streaming hello
     *
     * @param user  user name
     * @param depth depth of nested calls, {@literal 0} for simple calls, use positive value for nesting
     * @return an hello statement
     */
    public String sayHelloMany(String user, int depth) {

        CountDownLatch latch = new CountDownLatch(1);

        Req request = buildRequest(user, depth);

        StringBuilder sb = new StringBuilder();

        StreamObserver<Rep> streamObserver = concatenateStreamObserver(latch, sb);

        doSayHelloMany(request, streamObserver);

        awaitLatch(latch);

        return sb.toString();
    }

    protected abstract void doSayHelloMany(Req request, StreamObserver<Rep> streamObserver);

    public String sayHelloManyMany(List<String> users, int depth) {
        CountDownLatch latch = new CountDownLatch(1);

        StringBuilder sb = new StringBuilder();

        StreamObserver<Rep> streamObserver = concatenateStreamObserver(latch, sb);

        StreamObserver<Req> requestStream = doSayHelloManyMany(streamObserver);

        for (String user : users) {
            requestStream.onNext(buildRequest(user, depth));
        }

        // we use a specific message to make server end the call
        requestStream.onNext(buildRequest("nobody", 0));

        // client still needs to complete the call, otherwise server will still have a request pending which
        // prevents proper server shutdown.
        requestStream.onCompleted();

        awaitLatch(latch);

        return sb.toString();
    }

    protected abstract StreamObserver<Req> doSayHelloManyMany(StreamObserver<Rep> streamObserver);

    private static void awaitLatch(CountDownLatch latch) {
        try {
            boolean await = latch.await(1, TimeUnit.SECONDS);
            if (!await) {
                throw new IllegalStateException("giving up waiting for latch, something is wrong");
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private StreamObserver<Rep> concatenateStreamObserver(CountDownLatch latch, StringBuilder sb) {
        return new StreamObserver<>() {
            @Override
            public void onNext(Rep rep) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(getResponseMessage(rep));
            }

            @Override
            public void onError(Throwable throwable) {
                errorCount.incrementAndGet();
                throw new IllegalStateException("unexpected error", throwable);
            }

            @Override
            public void onCompleted() {
                // server terminated call
                latch.countDown();
            }
        };
    }


    public final void stop() throws InterruptedException {
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

}
