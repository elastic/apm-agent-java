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
package co.elastic.apm.agent.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;

class ApmServerHealthChecker implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ApmServerHealthChecker.class);

    private final ReporterConfiguration reporterConfiguration;
    private final ApmServerClient apmServerClient;

    ApmServerHealthChecker(ReporterConfiguration reporterConfiguration, ApmServerClient apmServerClient) {
        this.reporterConfiguration = reporterConfiguration;
        this.apmServerClient = apmServerClient;
    }

    @Override
    public void run() {
        boolean success = false;
        HttpURLConnection connection = null;
        for (int i = 0; i < reporterConfiguration.getServerUrls().size() && !success; i++) {
            String message;
            try {
                connection = apmServerClient.startRequest("/");
                if (logger.isDebugEnabled()) {
                    logger.debug("Starting healthcheck to {}", connection.getURL());
                }

                final int status = connection.getResponseCode();

                success = status < 300;

                if (!success) {
                    if (status == 404) {
                        message = "It seems like you are using a version of the APM Server which is not compatible with this agent. " +
                            "Please use APM Server 6.5.0 or newer.";
                    } else {
                        message = Integer.toString(status);
                    }
                } else {
                    // prints out the version info of the APM Server
                    message = HttpUtils.getBody(connection);
                }
            } catch (IOException e) {
                message = e.getMessage();
                success = false;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            if (success) {
                logger.info("Elastic APM server is available: {}", message);
            } else {
                apmServerClient.switchToNextServerUrl();
                logger.warn("Elastic APM server is not available ({})", message);
            }
        }
    }
}
