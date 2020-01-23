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

public class GrpcApp {

    private static final Logger logger = LoggerFactory.getLogger(GrpcApp.class);

    private static final int PORT = 50051;

    public static void main(String[] args) throws IOException, InterruptedException {
        HelloServer server = new HelloServer();
        server.start(PORT);

        HelloClient client = new HelloClient("localhost", PORT);

        sendMessage(client, "bob");
        sendMessage(client, null);

        client.stop();
        server.stop();
    }

    static void sendMessage(HelloClient client, String name) {
        client.sayHello(name).ifPresentOrElse(
            m -> logger.info("received message = {}", m),
            () -> logger.error("oops! something went wrong")
        );
    }


}
