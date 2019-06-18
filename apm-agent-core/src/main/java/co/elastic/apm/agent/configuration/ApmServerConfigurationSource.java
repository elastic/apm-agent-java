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
package co.elastic.apm.agent.configuration;

import co.elastic.apm.agent.impl.MetaData;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.serialize.PayloadSerializer;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonReader;
import com.dslplatform.json.MapConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.source.AbstractConfigurationSource;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.Map;

public class ApmServerConfigurationSource extends AbstractConfigurationSource {
    private static final Logger logger = LoggerFactory.getLogger(ApmServerConfigurationSource.class);

    private final DslJson<Object> dslJson = new DslJson<>();
    private final byte[] buffer = new byte[4096];
    private final PayloadSerializer payloadSerializer;
    private final MetaData metaData;
    private final ApmServerClient apmServerClient;
    @Nullable
    private String etag;
    private Map<String, String> config = Collections.emptyMap();

    public ApmServerConfigurationSource(PayloadSerializer payloadSerializer, MetaData metaData, ApmServerClient apmServerClient) {
        this.payloadSerializer = payloadSerializer;
        this.metaData = metaData;
        this.apmServerClient = apmServerClient;
    }

    @Override
    public void reload() throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = apmServerClient.startRequest("/config/v1/agents");
            if (logger.isDebugEnabled()) {
                logger.debug("Reloading configuration from APM Server {}", connection.getURL());
            }
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            if (etag != null) {
                connection.setRequestProperty("If-None-Match", etag);
            }
            payloadSerializer.setOutputStream(connection.getOutputStream());
            payloadSerializer.serializeMetadata(metaData);
            payloadSerializer.flush();

            final int status = connection.getResponseCode();
            if (status == 404) {
                logger.info("This version of the APM Server does not allow fetching configuration. " +
                    "Update to APM Server 7.3 to take advantage of centralized configuration.");
            } else if (status == 304) {
                logger.debug("Configuration did not change");
            } else {
                etag = connection.getHeaderField("ETag");
                final JsonReader<Object> reader = dslJson.newReader(connection.getInputStream(), buffer);
                reader.startObject();
                config = MapConverter.deserialize(reader);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public String getValue(String key) {
        return config.get(key);
    }

    @Override
    public String getName() {
        return "APM Server";
    }

}
