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
package co.elastic.apm.agent.spring.webflux.testapp;

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

@SpringBootApplication
public class WebFluxApplication {

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        List<String> arguments = Arrays.asList(args);
        int port = parseIntOption(arguments, "--port", DEFAULT_PORT);
        run(port);
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
    public static App run(int port) {
        if (port < 0) {
            port = getAvailableRandomPort();
        }

        SpringApplication app = new SpringApplication(WebFluxApplication.class);
        app.setDefaultProperties(Map.of("server.port", port));
        return new App(port, app.run());
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
