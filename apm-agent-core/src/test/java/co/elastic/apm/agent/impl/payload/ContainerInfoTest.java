/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.agent.impl.payload;

import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;

public class ContainerInfoTest {
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    void testContainerIdParsing() {
        String validId = "3741401135a8d27237e2fb9c0fb2ecd93922c0d1dd708345451e479613f8d4ae";
        String validLinePrefix = "7:freezer:/path/";
        assertContainerId(validLinePrefix + validId, validId);
        assertContainerInfoIsNull(validLinePrefix.substring(2) + validId);
        assertContainerId(validLinePrefix + "docker-" + validId + ".scope", validId);
        assertContainerId(validLinePrefix + validId + ".scope", validId);
        assertContainerInfoIsNull(validLinePrefix + validId.replace('f', 'g'));
        assertContainerInfoIsNull(validLinePrefix + validId.substring(1));
        assertContainerInfoIsNull(validLinePrefix + validId.concat("7"));
        assertContainerInfoIsNull(validLinePrefix + validId.concat("p"));
        assertContainerInfoIsNull("5:rdma:/");
        assertContainerInfoIsNull("0::/system.slice/docker.service");
    }

    @Test
    void testEcsFormat() {
        String id = "7e9139716d9e5d762d22f9f877b87d1be8b1449ac912c025a984750c5dbff157";
        assertContainerId("3:cpuacct:/ecs/eb9d3d0c-8936-42d7-80d8-f82b2f1a629e/" + id, id);

        // based on https://github.com/aws/amazon-ecs-agent/issues/1119 - supporting Docker on ECS
        assertContainerId("9:perf_event:/ecs/task-arn/" + id, id);
    }

    @Test
    void testKubernetesInfo() {
        // 1:name=systemd:/kubepods/besteffort/pode9b90526-f47d-11e8-b2a5-080027b9f4fb/15aa6e53-b09a-40c7-8558-c6c31e36c88a
        String containerId = "15aa6e53-b09a-40c7-8558-c6c31e36c88a";
        String podId = "e9b90526-f47d-11e8-b2a5-080027b9f4fb";
        String line = "1:name=systemd:/kubepods/besteffort/pod" + podId + "/" + containerId;
        SystemInfo systemInfo = assertContainerId(line, containerId);
        assertKubernetesInfo(systemInfo, podId, "my-host", null, null);

        // 1:name=systemd:/kubepods.slice/kubepods-burstable.slice/kubepods-burstable-pod90d81341_92de_11e7_8cf2_507b9d4141fa.slice/
        //                                                      crio-2227daf62df6694645fee5df53c1f91271546a9560e8600a525690ae252b7f63.scope
        containerId = "2227daf62df6694645fee5df53c1f91271546a9560e8600a525690ae252b7f63";
        podId = "90d81341_92de_11e7_8cf2_507b9d4141fa";
        line = "1:name=systemd:/kubepods.slice/kubepods-burstable.slice/kubepods-burstable-pod" + podId + ".slice/crio-" + containerId +
            ".scope";
        systemInfo = assertContainerId(line, containerId);
        assertKubernetesInfo(systemInfo, podId, "my-host", null, null);
    }

    @Test
    void testKubernetesDownwardApi() {
        String line = "1:name=systemd:/kubepods/besteffort/pode9b90526-f47d-11e8-b2a5-080027b9f4fb/15aa6e53-b09a-40c7-8558-c6c31e36c88a";
        String containerId = "15aa6e53-b09a-40c7-8558-c6c31e36c88a";
        SystemInfo systemInfo = assertContainerId(line, containerId);

        String originalPodUid = "e9b90526-f47d-11e8-b2a5-080027b9f4fb";
        String hostName = "my-host";
        assertKubernetesInfo(systemInfo, originalPodUid, hostName, null, null);

        String podUid = "downward-api-pod-uid";
        String podName = "downward-api-pod-name";
        String nodeName = "downward-api-node-name";
        String namespace = "downward-api-namespace";
        environmentVariables.set("KUBERNETES_NODE_NAME", nodeName);
        environmentVariables.set("KUBERNETES_POD_NAME", podName);
        environmentVariables.set("KUBERNETES_NAMESPACE", namespace);
        environmentVariables.set("KUBERNETES_POD_UID", podUid);
        systemInfo.findContainerDetails();
        assertKubernetesInfo(systemInfo, podUid, podName, nodeName, namespace);

        // test partial settings
        systemInfo = assertContainerId(line, containerId);
        assertKubernetesInfo(systemInfo, originalPodUid, hostName, null, null);
        environmentVariables.clear("KUBERNETES_POD_UID", "KUBERNETES_POD_NAME");
        systemInfo.findContainerDetails();
        assertKubernetesInfo(systemInfo, originalPodUid, hostName, nodeName, namespace);

        environmentVariables.clear("KUBERNETES_NAMESPACE", "KUBERNETES_NODE_NAME");
    }

    private SystemInfo createSystemInfo() {
        return new SystemInfo("arch", "my-host", "platform");
    }

    private SystemInfo assertContainerId(String line, String containerId) {
        SystemInfo systemInfo = createSystemInfo();
        assertThat(systemInfo.parseContainerId(line).getContainerInfo()).isNotNull();
        //noinspection ConstantConditions
        assertThat(systemInfo.getContainerInfo().getId()).isEqualTo(containerId);
        return systemInfo;
    }

    private void assertContainerInfoIsNull(String line) {
        SystemInfo systemInfo = createSystemInfo();
        assertThat(systemInfo.parseContainerId(line).getContainerInfo()).isNull();
    }

    private void assertKubernetesInfo(SystemInfo systemInfo, @Nullable String podUid, @Nullable String podName, @Nullable String nodeName,
                                      @Nullable String nameSpace) {
        assertThat(systemInfo.getKubernetesInfo()).isNotNull();
        SystemInfo.Kubernetes.Pod pod = systemInfo.getKubernetesInfo().getPod();
        if (podUid == null && podName == null) {
            assertThat(pod).isNull();
        } else {
            //noinspection ConstantConditions
            assertThat(pod.getName()).isEqualTo(podName);
            assertThat(pod.getUid()).isEqualTo(podUid);
        }
        SystemInfo.Kubernetes.Node node = systemInfo.getKubernetesInfo().getNode();
        if (nodeName == null) {
            assertThat(node).isNull();
        } else {
            //noinspection ConstantConditions
            assertThat(node.getName()).isEqualTo(nodeName);
        }
        assertThat(systemInfo.getKubernetesInfo().getNamespace()).isEqualTo(nameSpace);
    }
}
