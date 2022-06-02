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
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.util.ExecutorUtils;
import co.elastic.apm.agent.util.Version;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonReader;
import com.dslplatform.json.Nullable;
import com.dslplatform.json.ObjectConverter;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ApmServerHealthChecker implements Callable<Version> {
    private static final Logger logger = LoggerFactory.getLogger(ApmServerHealthChecker.class);

    private static final DslJson<Object> dslJson = new DslJson<>(new DslJson.Settings<>());

    private final ApmServerClient apmServerClient;

    public ApmServerHealthChecker(ApmServerClient apmServerClient) {
        this.apmServerClient = apmServerClient;
    }

    public Future<Version> checkHealthAndGetMinVersion() {
        ThreadPoolExecutor pool = ExecutorUtils.createSingleThreadDaemonPool("server-healthcheck", 1);
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
                            Version version = parseVersion(body);
                            if (logger.isDebugEnabled()) {
                                logger.debug("APM server {} version is: {}", connection.getURL(), version);
                            }
                            return version;
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
        return Version.UNKNOWN_VERSION;
    }

    static Version parseVersion(String body) throws java.io.IOException {
        JsonReader<Object> reader = dslJson.newReader(body.getBytes(UTF_8));
        reader.startObject();
        String versionString;
        try {
            // APM server 7.0+ contain a flat map at the JSON root
            LinkedHashMap<String, Object> responseJsonMap = ObjectConverter.deserializeMap(reader);
            versionString = (String) Objects.requireNonNull(responseJsonMap.get("version"));
        } catch (Exception e) {
            // 6.x APM server versions' JSON has a root object of which value is the same map
            reader = dslJson.newReader(body.getBytes(UTF_8));
            reader.startObject();
            Map<String, Object> root = ObjectConverter.deserializeMap(reader);
            //noinspection unchecked
            versionString = ((Map<String, String>) root.get("ok")).get("version");
        }
        return Version.of(versionString);
    }
}
