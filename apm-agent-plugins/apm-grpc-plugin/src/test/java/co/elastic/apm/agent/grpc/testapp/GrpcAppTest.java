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

import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

// this class just tests the sample application normal behavior, not the behavior when it's instrumented
class GrpcAppTest {

    private static final Logger logger = LoggerFactory.getLogger(GrpcAppTest.class);

    private GrpcApp app;

    @BeforeEach
    void beforeEach() throws Exception {
        logger.info("--- test start ---");

        app = new GrpcApp();
        app.start();
    }

    @AfterEach
    void afterEach() throws Exception {
        app.stop();

        logger.info("--- test end ---");
    }

    @Test
    void simpleCall() {
        for (SendAndCheckMessageStrategy strategy : STRATEGIES) {
            strategy.sendAndCheckMessage(app, "joe", 0, "hello(joe)");
        }
    }

    @Test
    void simpleErrorCall() {
        for (SendAndCheckMessageStrategy strategy : STRATEGIES) {
            strategy.sendAndCheckMessage(app, null, 0, null);
            strategy.sendAndCheckMessage(app, "boom", 0, null);
        }
    }

    @Test
    void nestedChecks() {
        for (SendAndCheckMessageStrategy strategy : STRATEGIES) {
            strategy.sendAndCheckMessage(app, "joe", 0, "hello(joe)");
            strategy.sendAndCheckMessage(app, "bob", 1, "nested(1)->hello(bob)");
            strategy.sendAndCheckMessage(app, "rob", 2, "nested(2)->nested(1)->hello(rob)");
        }
    }

    @Test
    void recommendedServerErrorHandling() {
        for (SendAndCheckMessageStrategy strategy : STRATEGIES) {
            exceptionOrErrorCheck(strategy, null);
        }
    }

    @Test
    void uncaughtExceptionServerErrorHandling() {
        // should be strictly identical to "recommended way to handle errors" from client perspective
        // but might differ server side
        for (SendAndCheckMessageStrategy strategy : STRATEGIES) {
            exceptionOrErrorCheck(strategy, "boom");
        }
    }

    @Test
    void asyncCancelCallBeforeProcessing() {
        Future<String> msg = app.sendMessageAsync("bob", 0);
        msg.cancel(true);
        assertThat(msg).isCancelled();
    }

    @Test
    void asyncCancelCallWhileProcessingFutureCancel() throws BrokenBarrierException, InterruptedException {
        asyncCancelCallWhileProcessing(true);

    }

    @Test
    void asyncCancelCallWhileProcessingChannelTerminate() throws BrokenBarrierException, InterruptedException {
        asyncCancelCallWhileProcessing(false);
    }

    private void asyncCancelCallWhileProcessing(boolean futureCancel) throws BrokenBarrierException, InterruptedException {
        CyclicBarrier startBarrier = new CyclicBarrier(2);
        CyclicBarrier endBarrier = new CyclicBarrier(2);
        app.getServer().useBarriersForProcessing(startBarrier, endBarrier);

        Future<String> msg = app.sendMessageAsync("bob", 0);

        logger.info("server processing wait for start");
        startBarrier.await();
        logger.info("server processing has started");

        if (futureCancel) {
            msg.cancel(true);
        } else {
            // stop the client in order to make the channel unusable on purpose
            app.getClient().stop();
        }

        if(futureCancel){
            assertThat(msg).isCancelled();
        } else {
            // in case of stopped channel, future is not cancelled but should fail with a timeout
            assertThat(msg).isNotCancelled();

            assertThrows(TimeoutException.class, () -> msg.get(20, TimeUnit.MILLISECONDS));
        }

        endBarrier.await();
    }


    void exceptionOrErrorCheck(SendAndCheckMessageStrategy strategy, String name) {
        strategy.sendAndCheckMessage(app, name, 0, null);
        strategy.sendAndCheckMessage(app, name, 1, "nested(1)->error(0)");
        strategy.sendAndCheckMessage(app, name, 2, "nested(2)->nested(1)->error(0)");
    }

    private interface SendAndCheckMessageStrategy {
        void sendAndCheckMessage(GrpcApp app, String name, int depth, String expectedMsg);
    }

    private static final SendAndCheckMessageStrategy BLOCKING = (app, name, depth, expectedMsg) -> {
        logger.info("sending message using BLOCKING strategy");
        String msg = app.sendMessage(name, depth);
        assertThat(msg).isEqualTo(expectedMsg);
    };

    private static final SendAndCheckMessageStrategy ASYNC = (app, name, depth, expectedMsg) -> {
        logger.info("sending message using ASYNC strategy");
        Future<String> msg = app.sendMessageAsync(name, depth);
        try {
            assertThat(msg.get()).isEqualTo(expectedMsg);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof StatusRuntimeException) {
                logger.error("server error", e.getCause());
                return; // silently ignore TODO : add logging for server error
            }
            throw new RuntimeException(e);
        }
    };

    private static final List<SendAndCheckMessageStrategy> STRATEGIES = Arrays.asList(BLOCKING, ASYNC);
}
