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

import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public abstract class HelloClient<Req, Rep> {

    private static final Logger logger = LoggerFactory.getLogger(HelloClient.class);

    private final ManagedChannel channel;

    protected HelloClient(ManagedChannel channel) {
        this.channel = channel;
    }

    public abstract Req buildRequest(String user, int depth);

    public abstract Rep executeBlocking(Req request);

    public abstract ListenableFuture<Rep> executeAsync(Req request);

    public abstract String getResponseMessage(Rep response);

    public final String sayHello(String user, int depth) {
        Req request = buildRequest(user, depth);
        Rep reply;
        try {
            reply = executeBlocking(request);
        } catch (StatusRuntimeException e) {
            logger.error("server error {} {}", e.getStatus(), e.getMessage());
            return null;
        }
        return getResponseMessage(reply);
    }

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

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }

        return getResponseMessage(result.get());
    }

    protected abstract StreamObserver<Req> doSayManyHello(StreamObserver<Rep> responseObserver);

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
