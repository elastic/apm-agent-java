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
package co.elastic.apm.agent.log.shipper;

import co.elastic.apm.agent.report.AbstractIntakeApiHandler;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.agent.report.serialize.PayloadSerializer;
import com.dslplatform.json.JsonWriter;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.Set;

public class ApmServerLogShipper extends AbstractIntakeApiHandler implements FileChangeListener {

    public static final String LOGS_ENDPOINT = "/intake/v2/logs";
    private static final Logger logger = LoggerFactory.getLogger(ApmServerLogShipper.class);
    private long httpRequestClosingThreshold;
    @Nullable
    private File currentFile;
    private Set<TailableFile> tailableFiles = new HashSet<>();

    public ApmServerLogShipper(ApmServerClient apmServerClient, ReporterConfiguration reporterConfiguration, PayloadSerializer payloadSerializer) {
        super(reporterConfiguration, payloadSerializer, apmServerClient);
    }

    @Override
    public boolean onLineAvailable(TailableFile tailableFile, byte[] line, int offset, int length, boolean eol) throws Exception {
        tailableFiles.add(tailableFile);
        try {
            if (connection == null) {
                connection = startRequest(LOGS_ENDPOINT);
            }
            if (connection != null && os != null) {
                File file = tailableFile.getFile();
                if (!file.equals(currentFile)) {
                    currentFile = file;
                    writeFileMetadata(os, file);
                }
                write(os, line, offset, length, eol);
                return true;
            } else {
                logger.debug("Cannot establish connection to APM server, backing off log shipping.");
                onConnectionError(null, currentlyTransmitting, 0);
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

    @Override
    protected void onRequestSuccess() {
        super.onRequestSuccess();
        for (TailableFile tailableFile : tailableFiles) {
            tailableFile.ack();
        }
    }

    @Override
    protected void onRequestError(Integer responseCode, InputStream inputStream, @Nullable IOException e) {
        super.onRequestError(responseCode, inputStream, e);
        for (TailableFile tailableFile : tailableFiles) {
            tailableFile.nak();
        }
    }

    private void writeFileMetadata(OutputStream os, File file) throws IOException {
        JsonWriter jw = payloadSerializer.getJsonWriter();
        jw.reset();
        payloadSerializer.serializeFileMetaData(file);
        os.write(jw.getByteBuffer(), 0, jw.size());
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
    @Nullable
    protected HttpURLConnection startRequest(String endpoint) throws Exception {
        HttpURLConnection connection = super.startRequest(endpoint);
        httpRequestClosingThreshold = System.currentTimeMillis() + reporterConfiguration.getApiRequestTime().getMillis();
        currentFile = null;
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
