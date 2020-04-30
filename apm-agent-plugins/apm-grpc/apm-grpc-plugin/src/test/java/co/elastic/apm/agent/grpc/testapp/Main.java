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
import java.util.concurrent.ExecutionException;

public class Main {

    private static final int SLEEP = 200;

    protected static void doMain(GrpcApp app) {
        try {
            app.start();

            HelloClient<?, ?> client = app.getClient();

            for (int i = 1; i <= 3; i++) {
                System.out.println(String.format("---- run %d ----", i));

                System.out.println(client.sayHello("bob", i));
                Thread.sleep(SLEEP);

                System.out.println(client.sayHelloMany("alice", i));
                Thread.sleep(1000);

                System.out.println(client.sayManyHello(Arrays.asList("bob", "alice", "oscar"), i));
                Thread.sleep(1000);

                System.out.println(client.sayHelloManyMany(Arrays.asList("joe", "oscar"), i));
                Thread.sleep(1000);

                System.out.println(client.saysHelloAsync("async-user", i).get());
                Thread.sleep(1000);
            }


            app.stop();
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    protected static int parsePort(String[] args) {
        int port = 4242;
        try {
            port = Integer.parseInt(args[0]);
        } catch (RuntimeException e) {
            // silently ignored
        }
        return port;
    }
}
