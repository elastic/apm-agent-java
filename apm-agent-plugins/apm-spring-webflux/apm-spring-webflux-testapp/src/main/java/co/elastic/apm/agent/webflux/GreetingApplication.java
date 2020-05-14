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
package co.elastic.apm.agent.webflux;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

@SpringBootApplication
public class GreetingApplication {

    public static void main(String[] args) {
        try (ConfigurableApplicationContext context = run(8080)) {
            GreetingWebClient client = getClient(context);
            sampleRequests(client);
        }
    }

    /**
     * Starts application on provided port
     * @param port port to use
     * @return application context
     */
    public static ConfigurableApplicationContext run(int port){
        SpringApplication app = new SpringApplication(GreetingApplication.class);
        app.setDefaultProperties(Map.of("server.port", port));
        return app.run();
    }

    public static GreetingWebClient getClient(ConfigurableApplicationContext context){
        return context.getBean(GreetingWebClient.class);
    }

    public static void sampleRequests(GreetingWebClient client){
        System.out.println(client.getHelloMono());
        System.out.println(client.getMappingError404());
        System.out.println(client.getHandlerError());
        System.out.println(client.getMonoError());
        System.out.println(client.getMonoEmpty());
    }

}
