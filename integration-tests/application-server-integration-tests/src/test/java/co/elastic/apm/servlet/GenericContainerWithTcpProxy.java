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
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.terma.javaniotcpproxy.StaticTcpProxyConfig;
import com.github.terma.javaniotcpproxy.TcpProxy;
import org.testcontainers.containers.GenericContainer;

import javax.annotation.Nullable;
import java.util.concurrent.Future;

public class GenericContainerWithTcpProxy<SELF extends GenericContainerWithTcpProxy<SELF>> extends GenericContainer<SELF> {

    private static final Logger logger = LoggerFactory.getLogger(GenericContainerWithTcpProxy.class);
    private static final int DEFAULT_DEBUG_PORT = 5005;

    @Nullable
    private TcpProxy tcpProxy;
    private final int localPort;

    public GenericContainerWithTcpProxy(String dockerImageName) {
        this(dockerImageName, DEFAULT_DEBUG_PORT);
    }

    public GenericContainerWithTcpProxy(Future<String> image) {
        this(image, DEFAULT_DEBUG_PORT);
    }

    public GenericContainerWithTcpProxy(Future<String> image, int localPort) {
        super(image);
        this.localPort = localPort;
    }

    public GenericContainerWithTcpProxy(String dockerImageName, int localPort) {
        super(dockerImageName);
        this.localPort = localPort;
    }

    @Override
    protected void containerIsStarting(InspectContainerResponse containerInfo) {
        if (!AbstractServletContainerIntegrationTest.ENABLE_DEBUGGING) {
            return;
        }
        try {
            var config = new StaticTcpProxyConfig(
                localPort,
                getContainerIpAddress(),
                getMappedPort(localPort)
            );
            config.setWorkerCount(1);
            tcpProxy = new TcpProxy(config);
            tcpProxy.start();
        } catch (Exception e) {
            logger.warn("Exception while trying to start tcp debugger proxy", e);
        }
    }

    @Override
    protected void containerIsStopping(InspectContainerResponse containerInfo) {
        if (tcpProxy != null) {
            tcpProxy.shutdown();
        }
    }
}
