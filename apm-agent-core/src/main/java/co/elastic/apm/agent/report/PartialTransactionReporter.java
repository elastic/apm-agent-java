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

import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.objectpool.ObservableObjectPool;
import co.elastic.apm.agent.objectpool.ObjectPoolFactoryImpl;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import co.elastic.apm.agent.report.serialize.SerializationConstants;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.pooling.Allocator;
import co.elastic.apm.agent.util.UrlConnectionUtils;

import java.net.HttpURLConnection;

class PartialTransactionReporter {

    private static final int WRITER_POOL_SIZE = 8;

    private static final Logger logger = LoggerFactory.getLogger(PartialTransactionReporter.class);

    private final ApmServerClient apmServer;

    private final ObservableObjectPool<DslJsonSerializer.Writer> writerPool;

    private volatile boolean extensionSupportsPartialTransactions = true;

    public PartialTransactionReporter(ApmServerClient apmServer, final DslJsonSerializer payloadSerializer, ObjectPoolFactoryImpl poolFactory) {
        this.apmServer = apmServer;
        writerPool = poolFactory.createRecyclableObjectPool(WRITER_POOL_SIZE, new Allocator<DslJsonSerializer.Writer>() {
            @Override
            public DslJsonSerializer.Writer createInstance() {
                return payloadSerializer.newWriter();
            }
        });
    }

    public void reportPartialTransaction(TransactionImpl transaction) {
        if (!extensionSupportsPartialTransactions) {
            return;
        }
        String requestId = transaction.getFaas().getExecution();
        if (requestId == null || requestId.isEmpty()) {
            logger.debug("Not reporting partial transaction because requestId is not set: {}", transaction);
            return;
        }
        try {
            logger.debug("Reporting partial transaction {}", transaction);
            HttpURLConnection connection = apmServer.startRequest("/register/transaction");
            if (connection == null) {
                logger.debug("Cannot report partial transaction because server url is not configured");
                return;
            }

            try (UrlConnectionUtils.ContextClassloaderScope clScope = UrlConnectionUtils.withContextClassloaderOf(connection)) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setChunkedStreamingMode(SerializationConstants.BUFFER_SIZE);
                connection.setRequestProperty("Content-Type", "application/vnd.elastic.apm.transaction+ndjson");
                connection.setRequestProperty("x-elastic-aws-request-id", requestId);
                connection.setUseCaches(false);
                connection.connect();

                DslJsonSerializer.Writer writer = writerPool.createInstance();
                try {
                    writer.setOutputStream(connection.getOutputStream());
                    writer.blockUntilReady(); //should actually not block on AWS Lambda, as metadata is available immediately
                    writer.appendMetaDataNdJsonToStream();
                    writer.serializeTransactionNdJson(transaction);
                    writer.fullFlush();
                } finally {
                    writerPool.recycle(writer);
                }

                handleResponse(connection);
                connection.disconnect();
            }

        } catch (Exception e) {
            logger.error("Failed to report partial transaction {}", transaction, e);
        }
    }

    private void handleResponse(HttpURLConnection connection) throws Exception {
        int responseCode = connection.getResponseCode();
        if (responseCode >= 400 && responseCode <= 499) {
            extensionSupportsPartialTransactions = false;
            logger.debug("Response for registering partial transaction was {}, not registering any transactions anymore", responseCode);
        } else if (responseCode >= 200 && responseCode <= 299) {
            return;
        } else {
            logger.error("Request for registering partial transaction returned response code {}", responseCode);
        }
    }
}
