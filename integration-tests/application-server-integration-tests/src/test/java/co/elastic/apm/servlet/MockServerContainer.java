/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.servlet;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.mockserver.client.MockServerClient;
import org.testcontainers.containers.GenericContainer;

/**
 * Acts as a mock for the APM-server.
 * It stores the {@link co.elastic.apm.agent.impl.payload.Payload}s sent to it,
 * which can be retrieved via the {@link #client}.
 */
public class MockServerContainer extends GenericContainer<MockServerContainer> {

    private MockServerClient client;

    public MockServerContainer() {
        super("jamesdbloom/mockserver:mockserver-5.4.1");
        addExposedPorts(1080);
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        super.containerIsStarted(containerInfo);
        client = new MockServerClient(getContainerIpAddress(), getFirstMappedPort());
    }

    public MockServerClient getClient() {
        return client;
    }
}
