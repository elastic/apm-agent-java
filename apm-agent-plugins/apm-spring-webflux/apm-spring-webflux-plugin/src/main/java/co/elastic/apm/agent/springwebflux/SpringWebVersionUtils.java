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
package co.elastic.apm.agent.springwebflux;

import javax.annotation.Nullable;

public class SpringWebVersionUtils {

    private static final String SPRING_WEB_5_UTILS_CLASS_NAME = "co.elastic.apm.agent.springwebflux.SpringWeb5Utils";
    private static final String SPRING_WEB_6_UTILS_CLASS_NAME = "co.elastic.apm.agent.springwebflux.SpringWeb6Utils";

    @Nullable
    private static ISpringWebVersionUtils instance = null;

    private static volatile boolean initialized = false;

    private static synchronized void initialize() {
        if (initialized) {
            return;
        }
        try {
            // check if using spring 6.0.0 or higher
            Class.forName("org.springframework.http.HttpStatusCode");
            try {
                // loading class by name so to avoid linkage attempt when spring-web 6 is unavailable
                instance = (ISpringWebVersionUtils) Class.forName(SPRING_WEB_6_UTILS_CLASS_NAME).getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("Spring-web 6.x+ identified, but failed to load related utility class", e);
            }
        } catch (ClassNotFoundException ignored) {
            // assuming spring-web < 6.x
            try {
                // loading class by name so to avoid linkage attempt on spring-web 6, where the getStatusCode API has changed
                instance = (ISpringWebVersionUtils) Class.forName(SPRING_WEB_5_UTILS_CLASS_NAME).getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("Spring-web < 6.x identified, but failed to load related utility class", e);
            }
        } finally {
            initialized = true;
        }
    }

    @Nullable
    private static ISpringWebVersionUtils getImplementation() {
        if (!initialized) {
            initialize();
        }
        return instance;
    }

    public static int getStatusCode(Object response) {
        ISpringWebVersionUtils implementation = getImplementation();
        if (implementation != null) {
            return implementation.getStatusCode(response);
        }
        return 200;
    }

    public interface ISpringWebVersionUtils {
        int getStatusCode(Object response);
    }
}
