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

import co.elastic.apm.agent.impl.Telemetry;
import co.elastic.apm.agent.report.serialize.PayloadSerializer;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

public class TelemetryEventHandler extends AbstractIntakeApiHandler {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryEventHandler.class);

    private static final String TELEMETRY_URL = "/telemetry/v1/agents";

    protected TelemetryEventHandler(ReporterConfiguration reporterConfiguration, PayloadSerializer payloadSerializer, ApmServerClient apmServerClient) {
        super(reporterConfiguration, payloadSerializer, apmServerClient);
    }

    public void reportTelemetry(ReportingEvent event) {
        // TODO : add check on apm server version before trying to send telemetry
        try {
            connection = startRequest(TELEMETRY_URL);
            if (connection != null) {
                Telemetry telemetry = event.getTelemetry();
                if (telemetry != null) {
                    payloadSerializer.serializeTelemetry(telemetry);
                }
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Failed to get APM server connection, dropping event: {}", event);
                }
            }

        } catch (Exception e) {
            endRequestExceptionally();
        } finally {
            endRequest();
        }
    }

}
