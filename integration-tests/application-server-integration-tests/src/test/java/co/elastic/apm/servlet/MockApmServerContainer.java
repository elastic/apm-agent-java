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

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.ClearType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.ArrayList;
import java.util.List;

import static co.elastic.apm.agent.report.IntakeV2ReportingEventHandler.INTAKE_V2_URL;
import static org.mockserver.model.HttpRequest.request;

/**
 * Acts as a mock for the APM-server.
 */
public class MockApmServerContainer extends GenericContainer<MockApmServerContainer> {

    private static final String HEALTH_ENDPOINT = "/mockserver/status";
    private MockServerClient client;

    public static MockApmServerContainer mockServer() {
        return new MockApmServerContainer();
    }

    public static WiremockServerContainer wiremock() {
        return new WiremockServerContainer();
    }

    private MockApmServerContainer() {
        super("mockserver/mockserver:5.14.0");
        addEnv("MOCKSERVER_LIVENESS_HTTP_GET_PATH", HEALTH_ENDPOINT);
        addExposedPorts(1080);
        waitStrategy = Wait.forHttp(HEALTH_ENDPOINT).forStatusCode(200);
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        super.containerIsStarted(containerInfo);
        client = new MockServerClient(getHost(), getFirstMappedPort());
        client.when(request(INTAKE_V2_URL)).respond(HttpResponse.response().withStatusCode(200));
        client.when(request("/")).respond(HttpResponse.response().withStatusCode(200));
    }

    public List<String> getRecordedRequestBodies() {
        List<String> requests = new ArrayList<>();
        for (HttpRequest httpRequest : client.retrieveRecordedRequests(request(INTAKE_V2_URL))) {
            requests.add(httpRequest.getBodyAsString());
        }
        return requests;
    }

    public void clearRecorded(){
        client.clear(HttpRequest.request(), ClearType.LOG);
    }
}
