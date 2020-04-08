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
package co.elastic.apm.agent.log.shipper;

import co.elastic.apm.agent.impl.MetaData;
import co.elastic.apm.agent.report.AbstractIntakeApiHandler;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.agent.report.serialize.PayloadSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ApmServerLogShipper extends AbstractIntakeApiHandler implements FileChangeListener {

    public static final String LOGS_ENDPOINT = "/intake/v2/logs";
    private static final Logger logger = LoggerFactory.getLogger(ApmServerLogShipper.class);
    private long httpRequestClosingThreshold;

    public ApmServerLogShipper(ApmServerClient apmServerClient, ReporterConfiguration reporterConfiguration, MetaData metaData, PayloadSerializer payloadSerializer) {
        super(reporterConfiguration, metaData, payloadSerializer, apmServerClient);
    }

    @Override
    public boolean onLineAvailable(File file, byte[] line, int offset, int length, boolean eol) throws IOException {
        System.out.print(new String(line, offset, length, UTF_8));
        if (eol) {
            System.out.println();
        }
        try {
            if (connection == null) {
                connection = startRequest(LOGS_ENDPOINT);
            }
            if (os != null) {
                write(os, line, offset, length, eol);
                return true;
            }
        } catch (Exception e) {
            endRequest();
            if (shutDown) {
                // don't block shutdown by doing a backoff
                throw e;
            }
            logger.error("Failed send log with this error: {}", e.getMessage());
            onConnectionError(null, currentlyTransmitting, 0);
        }
        return false;
    }

    private void write(OutputStream os, byte[] line, int offset, int length, boolean eol) throws IOException {
        os.write(line, offset, length);
        if (eol) {
            currentlyTransmitting++;
            os.write('\n');
            if (shouldEndRequest()) {
                endRequest();
            }
        }
    }

    @Override
    protected HttpURLConnection startRequest(String endpoint) throws IOException {
        HttpURLConnection connection = super.startRequest(endpoint);
        httpRequestClosingThreshold = System.currentTimeMillis() + reporterConfiguration.getApiRequestTime().getMillis();
        return connection;
    }

    @Override
    public void onIdle() {
        if (shouldEndRequest()) {
            endRequest();
        }
    }

    @Override
    public void onShutdownInitiated() {
        // wakes up the file processing thread in case it's doing a backoff
        close();
    }

    @Override
    public void onShutdownComplete() {
        endRequest();
    }

    protected boolean shouldEndRequest() {
        return super.shouldEndRequest() || System.currentTimeMillis() > httpRequestClosingThreshold;
    }
}
