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

import co.elastic.apm.agent.util.ExecutorUtils;
import co.elastic.apm.agent.util.Version;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonReader;
import com.dslplatform.json.MapConverter;
import com.dslplatform.json.Nullable;
import com.dslplatform.json.ObjectConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ApmServerHealthChecker implements Callable<Version> {
    private static final Logger logger = LoggerFactory.getLogger(ApmServerHealthChecker.class);

    private final ApmServerClient apmServerClient;
    private final DslJson<Object> dslJson = new DslJson<>(new DslJson.Settings<>());

    public ApmServerHealthChecker(ApmServerClient apmServerClient) {
        this.apmServerClient = apmServerClient;
    }

    public Future<Version> checkHealthAndGetMinVersion() {
        ThreadPoolExecutor pool = ExecutorUtils.createSingleThreadDeamonPool("apm-server-healthcheck", 1);
        try {
            return pool.submit(this);
        } finally {
            pool.shutdown();
        }
    }

    @Nullable
    @Override
    public Version call() {
        List<Version> versions = apmServerClient.executeForAllUrls("/", new ApmServerClient.ConnectionHandler<Version>() {
            @Override
            public Version withConnection(HttpURLConnection connection) {
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
                        try {
                            // prints out the version info of the APM Server
                            String body = HttpUtils.readToString(connection.getInputStream());
                            logger.info("Elastic APM server is available: {}", body);
                            JsonReader<Object> reader = dslJson.newReader(body.getBytes(UTF_8));
                            reader.startObject();
                            String versionString;
                            try {
                                // newer APM server versions contain a flat map at the JSON root
                                versionString = MapConverter.deserialize(reader).get("version");
                            } catch (Exception e) {
                                // 6.x APM server versions' JSON has a root object of which value is the same map
                                reader = dslJson.newReader(body.getBytes(UTF_8));
                                reader.startObject();
                                Map<String, Object> root = ObjectConverter.deserializeMap(reader);
                                //noinspection unchecked
                                versionString = ((Map<String, String>) root.get("ok")).get("version");
                            }
                            if (logger.isDebugEnabled()) {
                                logger.debug("APM server {} version is: {}", connection.getURL(), versionString);
                            }
                            return new Version(versionString);
                        } catch (Exception e) {
                            logger.warn("Failed to parse version of APM server {}: {}", connection.getURL(), e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Elastic APM server {} is not available ({})", connection.getURL(), e.getMessage());
                }
                return null;
            }
        });
        versions.remove(null);
        if (!versions.isEmpty()) {
            return Collections.min(versions);
        }
        return null;
    }
}
