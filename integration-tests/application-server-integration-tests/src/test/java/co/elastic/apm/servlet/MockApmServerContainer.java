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
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.responseDefinition;
import static com.github.tomakehurst.wiremock.client.WireMock.any;

public class MockApmServerContainer extends GenericContainer<MockApmServerContainer> {

    private WireMock wireMock;

    public MockApmServerContainer() {
        super("wiremock/wiremock:3.13.2");
        addExposedPorts(8080);
        waitStrategy = Wait.forHealthcheck();
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        super.containerIsStarted(containerInfo);
        wireMock = WireMock.create()
            .host(getHost())
            .port(getMappedPort(8080))
            .build();

        wireMock.register(any(UrlPattern.ANY).willReturn(responseDefinition().withStatus(200)));
    }

    public List<String> getRecordedRequestBodies() {
        return wireMock.find(RequestPatternBuilder.newRequestPattern())
            .stream()
            // wiremock provides the raw request body without any decompression so we have to do it explicitly ourselves
            .map(action -> decompressZlib(action.getBody()))
            .collect(Collectors.toList());
    }

    private static String decompressZlib(byte[] input) {
        Inflater inflater = new Inflater();
        inflater.setInput(input);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                output.write(buffer, 0, count);
            }
            inflater.end();
            return output.toString(StandardCharsets.UTF_8);
        } catch (DataFormatException e) {
            throw new IllegalStateException(e);
        }
    }

    public void clearRecorded() {
        wireMock.resetRequests();
    }

}
