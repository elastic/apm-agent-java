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
        if (warmCount < 1) {
            warmCount = 1;
        }

        boolean waitForKeystroke = arguments.contains("--wait");

        if (isBench) {
            HelloServer.setVerbose(false);
        }

        try {
            app.start();
            HelloClient<?, ?> client = app.getClient();

            if (isBench) {
                System.out.println(String.format("benchmark warmup with count = %d", warmCount));
                execute(client, true, warmCount);
                System.out.println("benchmark warmup completed");

                if (waitForKeystroke) {
                    waitForKey();
                }
                System.out.println(String.format("benchmark with count = %d", count));
            }

            execute(client, isBench, count);

            if (isBench) {
                System.out.println("benchmark completed");

                if (waitForKeystroke) {
                    waitForKey();
                }
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
        for (int i = 1; i <= count; i++) {
            if (!isBench) {
                System.out.println(String.format("---- run %d ----", i));
            }

            String response = client.sayHello("bob", i);
            if (!isBench) {
                System.out.println(response);
                Thread.sleep(SLEEP);
            }

            response = client.sayHelloMany("alice", i);
            if (!isBench) {
                System.out.println(response);
                Thread.sleep(SLEEP);
            }

            response = client.sayManyHello(Arrays.asList("bob", "alice", "oscar"), i);
            if (!isBench) {
                System.out.println(response);
                Thread.sleep(SLEEP);
            }

            response = client.sayHelloManyMany(Arrays.asList("joe", "oscar"), i);
            if (!isBench) {
                System.out.println(response);
                Thread.sleep(SLEEP);
            }

            response = client.saysHelloAsync("async-user", i).get();
            if (!isBench) {
                System.out.println(response);
                Thread.sleep(SLEEP);
            }
        }
        long timeSpent = System.currentTimeMillis() - start;
        System.out.println(String.format("completed %d iterations in %d ms, average = %.02f ms", count, timeSpent, timeSpent * 1D / count));
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
        return value;
    }
}
