/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.springwebflux.testapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import javax.net.ServerSocketFactory;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@SpringBootApplication
public class WebFluxApplication {

    private static final Logger logger = LoggerFactory.getLogger(WebFluxApplication.class);

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        List<String> arguments = Arrays.asList(args);
        int port = Integer.parseInt(parseOption(arguments, "--port", Integer.toString(DEFAULT_PORT)));
        String server = parseOption(arguments, "--server", "netty");
        int count = Integer.parseInt(parseOption(arguments, "--count", "0"));

        boolean isClient = arguments.contains("--client");

        if (isClient) {
            if (count <= 0) {
                // execute at least once, otherwise it's useless
                count = 1;
            }
            doSampleRequests(useFunc -> new GreetingWebClient("localhost", port, useFunc), count);
        } else {
            // start the whole server & client
            App app = run(port, server);
            if (doSampleRequests(app::getClient, count)) {
                // shutdown app when using sample requests, otherwise let it run like a regular spring boot app
                app.close();
            }
        }
    }

    private static boolean doSampleRequests(Function<Boolean,GreetingWebClient> clientProvider, int count) {
        for (int i = 0; i < count; i++) {
            for (Boolean functional : Arrays.asList(true, false)) {
                logger.info("sample request {} / {} ({} endpoint)", i + 1, count, functional ? "functional" : "annotated");

                GreetingWebClient client = clientProvider.apply(functional);
                client.getHelloMono();
                client.getMappingError404();
                client.getHandlerError();
                client.getMonoError();
                client.getMonoEmpty();

                for (String method : Arrays.asList("GET", "POST", "PUT", "DELETE")) {
                    client.methodMapping(method);
                }
                client.withPathParameter("12345");
            }
        }
        return count > 0;
    }

    /**
     * Stores application state, using inner-class to avoid interfering with Spring boot application
     */
    public static class App implements Closeable {
        private final int port;
        private final ConfigurableApplicationContext context;

        private App(int port, ConfigurableApplicationContext context) {
            this.port = port;
            this.context = context;
        }

        public GreetingWebClient getClient(boolean useFunctional) {
            return new GreetingWebClient("localhost", port, useFunctional);
        }

        @Override
        public void close() {
            context.close();
        }
    }

    /**
     * Starts application on provided port
     *
     * @param port port to use
     * @return application context
     */
    public static App run(int port, String server) {
        if (port < 0) {
            port = getAvailableRandomPort();
        }

        SpringApplication app = new SpringApplication(WebFluxApplication.class);
        app.setDefaultProperties(Map.of(
            "server.port", port,
            "server", server
        ));
        return new App(port, app.run());
    }

    private static String parseOption(List<String> arguments, String option, String defaultValue) {
        int index = arguments.indexOf(option);
        if (index < 0 || arguments.size() <= index) {
            return defaultValue;
        }
        return arguments.get(index + 1);
    }

    private static int getAvailableRandomPort() {
        int port;
        try (ServerSocket socket = ServerSocketFactory.getDefault().createServerSocket(0, 1, InetAddress.getByName("localhost"))) {
            port = socket.getLocalPort();
        } catch (IOException e) {
            port = DEFAULT_PORT;
        }
        return port;
    }

}
