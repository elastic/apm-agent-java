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
package co.elastic.apm.servlet;

import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import okio.InflaterSource;
import org.junit.rules.ExternalResource;
import org.testcontainers.Testcontainers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.Inflater;

import static co.elastic.apm.agent.report.IntakeV2ReportingEventHandler.INTAKE_V2_FLUSH_URL;
import static co.elastic.apm.agent.report.IntakeV2ReportingEventHandler.INTAKE_V2_URL;

/**
 * This is modeled after {@code co.elastic.apm.awslambda.fakeserver.FakeApmServer}, except
 * deflate instead of gzip and OkHttp instead of {@code com.sun.net.httpserver.HttpServer}.
 */
public class ApmServerRule extends ExternalResource {
    private static final Logger logger = LoggerFactory.getLogger(ApmServerRule.class);

    private final Queue<String> receivedIntakeEvents = new ConcurrentLinkedQueue<>();
    private final MockWebServer apmServer = newApmServer(receivedIntakeEvents);
    private boolean started;

    private static MockWebServer newApmServer(Queue<String> receivedIntakeEvents) {
        MockWebServer apmServer = new MockWebServer();
        apmServer.setDispatcher(new ApmDispatcher(receivedIntakeEvents));
        return apmServer;
    }

    /**
     * Start the mock apm server and expose it to any container as {@code host.testcontainers.internal:$port}.
     */
    @Override
    protected void before() throws Throwable {
        if (started) { // guard as MockWebServer cannot be started multiple times
            return;
        } else {
            started = true;
        }

        apmServer.start();
        Testcontainers.exposeHostPorts(apmServer.getPort());
    }

    @Override
    protected void after() {
        try {
            apmServer.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void clearIntakeEvents() {
        receivedIntakeEvents.clear();
    }

    public List<String> getIntakeEvents() {
        return new ArrayList<>(receivedIntakeEvents);
    }

    public int getPort() {
        return apmServer.getPort();
    }


    private static final class ApmDispatcher extends Dispatcher {
        private final Queue<String> receivedIntakeEvents;

        private ApmDispatcher(Queue<String> receivedIntakeEvents) {
            this.receivedIntakeEvents = receivedIntakeEvents;
        }

        @Override
        public MockResponse dispatch(RecordedRequest request) {
            switch (request.getPath()) {
                case "/": // health check
                    return new MockResponse().setBody("{\"version\": \"8.7.1\"}");
                case INTAKE_V2_URL:
                case INTAKE_V2_FLUSH_URL:
                    try {
                        readIntakeEvents(request);
                    } catch (IOException e) {
                        logger.error("Encountered intake request error {}", e);
                        return new MockResponse().setResponseCode(500);
                    }
                    return new MockResponse().setResponseCode(202);
            }
            logger.error("Encountered unexpected request {}", request);
            return new MockResponse().setResponseCode(404);
        }

        private void readIntakeEvents(RecordedRequest request) throws IOException {
            // The request body is compressed with deflate
            final Buffer body;
            String encoding = request.getHeader("Content-Encoding");
            if (encoding == null) {
                body = request.getBody().buffer();
            } else if (encoding.contains("deflate")) {
                Buffer inflated = new Buffer();
                try (InflaterSource deflated = new InflaterSource(request.getBody(), new Inflater())) {
                    while (deflated.read(inflated, Integer.MAX_VALUE) != -1) ;
                }
                body = inflated;
            } else {
                throw new IOException("unsupported encoding: " + encoding);
            }

            // read each Newline Delimited JSON event into the queue.
            String event;
            while ((event = body.readUtf8Line()) != null) {
                if (!event.startsWith("{")) {
                    throw new IOException("not NDJSON " + request);
                } else {
                    receivedIntakeEvents.add(event);
                }
            }
        }
    }
}
