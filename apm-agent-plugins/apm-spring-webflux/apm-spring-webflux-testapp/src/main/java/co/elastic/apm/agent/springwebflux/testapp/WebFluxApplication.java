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
package co.elastic.apm.agent.springwebflux.testapp;

import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import javax.net.ServerSocketFactory;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        boolean logEnabled = arguments.contains("--log");

        boolean isBench = arguments.contains("--benchmark");
        boolean waitForKey = arguments.contains("--wait");

        if (isBench) {
            // start the whole server & client
            App app = run(port, server, logEnabled);

            count = Math.max(count, 100); // any smaller benchmark is not meaningful
            try {
                if (waitForKey) {
                    waitForKey();
                }

                // warmup has same duration as benchmark to help having consistent results
                logger.info("warmup requests ({})", count);
                doSampleRequests(app::getClient, count);
                logger.info("warmup complete");

                if (waitForKey) {
                    waitForKey();
                }

                logger.info("start benchmark ({})", count);
                doSampleRequests(app::getClient, count);
                logger.info("benchmark complete");

                if (waitForKey) {
                    waitForKey();
                }


            } finally {
                app.close();
            }
        } else if (isClient) {
            count = Math.max(count, 1);
            doSampleRequests(useFunc -> new GreetingWebClient("localhost", port, useFunc, logEnabled), count);
        } else {
            // leave server running
        }

    }

    private static void doSampleRequests(Function<Boolean, GreetingWebClient> clientProvider, int count) {
        List<GreetingWebClient> clients = Stream.of(true, false)
            .map(clientProvider)
            .collect(Collectors.toList());

        long start = System.currentTimeMillis();
        int statusFrequency = count <= 10 ? 1 : count / 10;

        long timeLastUpdate = start;
        int countLastUpdate = 0;

        for (int i = 1; i <= count; i++) {
            clients.forEach(GreetingWebClient::sampleRequests);

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

    /**
     * Stores application state, using inner-class to avoid interfering with Spring boot application
     */
    public static class App implements Closeable {
        private final int port;
        private final ConfigurableApplicationContext context;
        private final boolean logEnabled;

        private App(int port, ConfigurableApplicationContext context, boolean logEnabled) {
            this.port = port;
            this.context = context;
            this.logEnabled = logEnabled;
        }

        public GreetingWebClient getClient(boolean useFunctional) {
            return new GreetingWebClient("localhost", port, useFunctional, logEnabled);
        }

        @Override
        public void close() {
            context.close();
        }
    }

    /**
     * Starts application on provided port
     *
     * @param port       port to use
     * @param server     server implementation to use
     * @param logEnabled true to enable client and server logging (very verbose, better for debugging)
     * @return application context
     */
    public static App run(int port, String server, boolean logEnabled) {
        if (port < 0) {
            port = getAvailableRandomPort();
        }

        SpringApplication app = new SpringApplication(WebFluxApplication.class);
        Map<String, Object> appProperties = new HashMap<>();
        app.setBannerMode(Banner.Mode.OFF);
        appProperties.put("server.port", port);
        appProperties.put("server", server);
        appProperties.put("logging.level.org.springframework", logEnabled ? "ERROR" : "OFF");
        app.setDefaultProperties(appProperties);

        return new App(port, app.run(), logEnabled);
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

    private static void waitForKey() {
        System.out.println("hit any key to continue");
        try {
            System.in.read();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
