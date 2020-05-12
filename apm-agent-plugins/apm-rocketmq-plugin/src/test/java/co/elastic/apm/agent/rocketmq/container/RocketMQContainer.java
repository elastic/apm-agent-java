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
package co.elastic.apm.agent.rocketmq.container;

import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;

public class RocketMQContainer extends GenericContainer<RocketMQContainer> {

    private static final String ROCKETMQ_IMAGE = "rocketmqinc/rocketmq";

    private static final int ROCKETMQ_NAME_SRV_PORT = 9876;

    private static final int ROCKET_BROKER_VIP_PORT = 10909;

    private static final int ROCKETMQ_BROKER_SERVICE_PORT = 10911;

    private String brokerConf;

    public RocketMQContainer() {
        this("4.2.0");
    }

    public RocketMQContainer(String version) {
        super(ROCKETMQ_IMAGE + ":" + version);
        this.brokerConf = "/opt/rocketmq-" + version + "/conf/broker.conf";
        withEnv("NAMESRV_ADDR", "localhost:" + ROCKETMQ_NAME_SRV_PORT);
        withCommand("sh mqbroker -c " + brokerConf);
        withExposedPorts(ROCKETMQ_NAME_SRV_PORT, ROCKET_BROKER_VIP_PORT, ROCKETMQ_BROKER_SERVICE_PORT);
        setPortBindings(Arrays.asList(
            ROCKETMQ_NAME_SRV_PORT + ":" + ROCKETMQ_NAME_SRV_PORT,
            ROCKET_BROKER_VIP_PORT + ":" + ROCKET_BROKER_VIP_PORT,
            ROCKETMQ_BROKER_SERVICE_PORT + ":" + ROCKETMQ_BROKER_SERVICE_PORT
        ));
    }

    public String getNameServer() {
        return "localhost:" + ROCKETMQ_NAME_SRV_PORT;
    }

    @Override
    protected void containerIsStarting(InspectContainerResponse containerInfo, boolean reused) {
        super.containerIsStarting(containerInfo, reused);

        if (reused) {
            return;
        }

        startNameServer();

        String localIp = getLocalIp();

        if (localIp == null) {
            throw new RuntimeException("Error in get local ip");
        }

        String properties = "brokerClusterName = DefaultCluster\n" +
            "brokerName = broker-01\n" +
            "brokerId = 1\n" +
            "deleteWhen = 04\n" +
            "fileReservedTime = 48\n" +
            "brokerRole = ASYNC_MASTER\n" +
            "flushDiskType = ASYNC_FLUSH\n" +
            "autoCreateTopicEnable = false\n" +
            "brokerIP1 = " + localIp + "\n";

        copyFileToContainer(
            Transferable.of(properties.getBytes(StandardCharsets.UTF_8), 700),
            brokerConf
        );

    }

    private void startNameServer() {
        execCmd("sh", "mqnamesrv");
    }

    private ExecStartResultCallback execCmd(String... cmds) {
        OutputStream outputStream = new ByteArrayOutputStream();
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(getContainerId())
            .withCmd(cmds)
            .exec();
        try {
            ExecStartResultCallback resultCallback = dockerClient.execStartCmd(execCreateCmdResponse.getId())
                .exec(new ExecStartResultCallback())
                .awaitCompletion();
            System.out.println(((ByteArrayOutputStream) outputStream).toString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getLocalIp() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();

                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> ias = ni.getInetAddresses();

                if (!ias.hasMoreElements()) {
                    continue;
                }

                while (ias.hasMoreElements()) {
                    InetAddress ia = ias.nextElement();
                    if (ia.isLoopbackAddress()) {
                        continue;
                    }

                    if (!(ia instanceof Inet4Address)) {
                        continue;
                    }

                    return ia.getHostAddress();
                }
            }
        } catch (Exception ignore){

        }
        return null;
    }


}
