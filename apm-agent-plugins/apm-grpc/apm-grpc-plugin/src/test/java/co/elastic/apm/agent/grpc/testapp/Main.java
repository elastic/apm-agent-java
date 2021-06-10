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
package co.elastic.apm.agent.grpc.testapp;

import co.elastic.apm.agent.testutils.TestPort;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class Main {

    private static final int SLEEP = 200;
    private static final int DEFAULT_COUNT = 3;

    protected static void doMain(GrpcApp app, String[] args) {

        List<String> arguments = Arrays.asList(args);
        int count = parseIntOption(arguments, "--count", DEFAULT_COUNT);

        boolean isBench = arguments.contains("--benchmark");
        int warmCount = isBench ? count / 10 : 0;
        if (warmCount < 100) {
            warmCount = 100;
        }

        boolean waitForKeystroke = arguments.contains("--wait");

        if (isBench) {
            HelloServer.setVerbose(false);
        }

        try {
            app.start();
            HelloClient<?, ?> client = app.getClient();

            if (isBench) {
                System.out.printf("benchmark warmup with count = %d%n", warmCount);
                execute(client, true, warmCount);
                System.out.println("benchmark warmup completed");

                if (waitForKeystroke) {
                    waitForKey();
                }
                System.out.printf("benchmark with count = %d%n", count);
            }

            execute(client, isBench, count);

            if (isBench) {
                System.out.println("benchmark completed");

                if (waitForKeystroke) {
                    waitForKey();
                }
            }

            long clientErrors = client.getErrorCount();
            if (clientErrors > 0) {
                System.out.printf("WARNING: client has reported %d errors, results might be incorrect%n", clientErrors);
            }

        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            try {
                app.stop();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static void waitForKey() throws IOException {
        System.out.println("hit any key to continue");
        System.in.read();
    }

    private static void execute(HelloClient<?, ?> client, boolean isBench, int count) throws InterruptedException, ExecutionException {
        long start = System.currentTimeMillis();
        int statusFrequency = count <= 10 ? 1: count / 10;

        long timeLastUpdate = start;
        int countLastUpdate = 0;
        for (int i = 1; i <= count; i++) {
            if (!isBench) {
                System.out.println(String.format("---- run %d ----", i));
            }

            for (int j = 0; j < 3; j++) {
                handleResponse(isBench, client.sayHello("bob", j));
                handleResponse(isBench, client.sayHelloMany("alice", j));
                handleResponse(isBench, client.sayManyHello(Arrays.asList("bob", "alice", "oscar"), j));
//                handleResponse(isBench, client.sayHelloManyMany(Arrays.asList("joe", "oscar"), j)); // TODO disabled for now as it throws exceptions
                handleResponse(isBench, client.saysHelloAsync("async-user", j).get());
            }

            if (i % statusFrequency == 0 || i == count) {
                long now = System.currentTimeMillis();
                long timeSpent = now - timeLastUpdate;
                int countSinceLastUpdate = i - countLastUpdate;
                System.out.printf("progress = %1$6.02f %% (%2$d), count = %3$d in %4$d ms, average = %5$.02f ms%n",
                    i * 100d / count,
                    i,
                    countSinceLastUpdate,
                    timeSpent,
                    timeSpent * 1d / countSinceLastUpdate
                );
                timeLastUpdate = now;
                countLastUpdate = i;

                if (i == count) {
                    long totalTime = System.currentTimeMillis() - start;
                    System.out.printf("total count = %d in %d ms, average = %.02f%n", count, totalTime, 1.0D * totalTime / count);
                }
            }
        }
    }

    private static void handleResponse(boolean isBench, String response) {
        if (isBench) {
            return;
        }
        System.out.println(response);
        try {
            Thread.sleep(SLEEP);
        } catch (InterruptedException e) {
            // silently ignored
        }
    }

    protected static int parsePort(String[] args) {
        List<String> arguments = Arrays.asList(args);
        return parseIntOption(arguments, "--port", 4242);
    }

    private static int parseIntOption(List<String> arguments, String option, int defaultValue) {
        int value = defaultValue;

        int portOptionIndex = arguments.indexOf(option);
        try {
            value = Integer.parseInt(arguments.get(portOptionIndex + 1));
        } catch (RuntimeException e) {
            // silently ignored
        }
        if (value < 0) {
            value = TestPort.getAvailableRandomPort();
        }
        return value;
    }
}
