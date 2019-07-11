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

import co.elastic.apm.agent.context.LifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.util.ExecutorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.util.concurrent.ThreadPoolExecutor;

public class ApmServerHealthChecker implements Runnable, LifecycleListener {
    private static final Logger logger = LoggerFactory.getLogger(ApmServerHealthChecker.class);

    private final ApmServerClient apmServerClient;

    public ApmServerHealthChecker(ApmServerClient apmServerClient) {
        this.apmServerClient = apmServerClient;
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        ThreadPoolExecutor pool = ExecutorUtils.createSingleThreadDeamonPool("apm-server-healthcheck", 1);
        pool.execute(this);
        pool.shutdown();
    }

    @Override
    public void run() {
        try {
            apmServerClient.executeForAllUrls("/", new ApmServerClient.ConnectionHandler<Void>() {
                @Override
                public Void withConnection(HttpURLConnection connection) {
                    try {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Starting healthcheck to {}", connection.getURL());
                        }

                        final int status = connection.getResponseCode();
                        if (status >= 300) {
                            if (status == 404) {
                                throw new IllegalStateException("It seems like you are using a version of the APM Server which is not compatible with this agent. " +
                                    "Please use APM Server 6.5.0 or newer.");
                            } else {
                                throw new IllegalStateException("Server returned status " + status);
                            }
                        } else {
                            // prints out the version info of the APM Server
                            logger.info("Elastic APM server is available: {}", HttpUtils.getBody(connection));
                        }
                    } catch (Exception e) {
                        logger.warn("Elastic APM server {} is not available ({})", connection.getURL(), e.getMessage());
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
    }
}
