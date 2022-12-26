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

import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

public class SpringWebUtilsFactory {

    private static final String SPRING_WEB_6_UTILS_CLASS_NAME = "co.elastic.apm.agent.springwebflux.SpringWeb6Utils";

    private static final Logger logger = LoggerFactory.getLogger(SpringWebUtilsFactory.class);
    private static SpringWebVersionUtils instance;

    static {
        try {
            Class<?> springWeb6Class = Class.forName("org.springframework.http.HttpStatusCode");
            try {
                // loading class by name sot to avoid linkage attempt when spring-web 6 is unavailable
                instance = (SpringWebVersionUtils) Class.forName(SPRING_WEB_6_UTILS_CLASS_NAME).getDeclaredConstructor().newInstance();
                logger.debug("Spring-web 6.x+ identified");
            } catch (Exception e) {
                logger.error("Spring-web 6.x+ identified, but failed to load related utility class", e);
                instance = new SpringWeb5Utils();
            }
        } catch (Exception e) {
            logger.debug("Spring-web < 6.x identified");
            instance = new SpringWeb5Utils();
        }
    }

    public static SpringWebVersionUtils getImplementation() {
        return instance;
    }
}
