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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;

public class GrpcApp {

    private HelloServer<?,?> server;
    private HelloClient<?,?> client;

    public GrpcApp(HelloServer<?,?> server, HelloClient<?,?> client) {
        this.server = server;
        this.client = client;
    }

    public void start() throws IOException {
        server.start();
    }

    public void stop() throws InterruptedException {
        client.stop();
        server.stop();
    }

    public String sayHello(String name, int depth) {
        return client.sayHello(name, depth);
    }

    public Future<String> sayHelloAsync(String name, int depth) {
        return client.saysHelloAsync(name, depth);
    }

    public String sayHelloClientStreaming(List<String> names, int depth) {
        return client.sayManyHello(names, depth);
    }

    public String sayHelloServerStreaming(String name, int depth){
        return client.sayHelloMany(name, depth);
    }

    public String sayHelloBidiStreaming(List<String> names, int depth){
        return client.sayHelloManyMany(names, depth);
    }

    public HelloServer<?,?> getServer() {
        return server;
    }

    public HelloClient<?,?> getClient() {
        return client;
    }
}
