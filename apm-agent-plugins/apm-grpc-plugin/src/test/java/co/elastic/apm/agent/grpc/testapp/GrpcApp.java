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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class GrpcApp {

    private static final Logger logger = LoggerFactory.getLogger(GrpcApp.class);

    private static final int PORT = 50051;
    private HelloServer server;
    private HelloClient client;

    public GrpcApp() {

    }

    public static void main(String[] args) throws IOException, InterruptedException {
        GrpcApp app = new GrpcApp();
        app.start();
        app.sampleRequests(null);
        app.stop();
    }

    public void start() throws IOException {
        server = new HelloServer(PORT);
        client = new HelloClient("localhost", PORT);
        server.start();
    }

    private void sampleRequests(HelloClient client) {
        sendMessage("bob", 0);
        sendMessage(null, 0);
        sendMessage("bob", 2);
        sendMessage(null, 2);
    }

    public void stop() throws InterruptedException {
        client.stop();
        server.stop();
    }

    public String sendMessage(String name, int depth) {
        Optional<String> response = client.sayHello(name, depth);
        if (!response.isPresent()) {
            logger.error("oops! something went wrong");
            return null;
        } else {
            String msg = response.get();
            logger.info("received message = {}", msg);
            return msg;
        }
    }


}
