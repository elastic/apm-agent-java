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
package co.elastic.apm.agent.impl.metadata;

import co.elastic.apm.agent.util.CustomEnvVariables;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import specs.TestJsonSpec;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class ContainerInfoTest extends CustomEnvVariables {

    @ParameterizedTest(name = "{0}")
    @MethodSource("getTestContainerMetadata")
    void testCommonContainerMetadata(String testName, Map<String,List<String>> files, @Nullable String containerId, @Nullable String podId) {

        SystemInfo systemInfo = createSystemInfo();
        for (Map.Entry<String, List<String>> fileEntry : files.entrySet()) {
            String fileName = fileEntry.getKey();
            List<String> fileContent = fileEntry.getValue();

            switch (fileName) {
                case "/proc/self/cgroup":
                    fileContent.forEach(systemInfo::parseCgroupsLine);
                    break;
                case "/proc/self/mountinfo":
                    systemInfo.parseCgroupsV2ContainerId(fileContent);
                    break;
                default:
                    throw new IllegalArgumentException("unknown test file :" + fileName);
            }
        }


        SystemInfo.Container containerInfo = systemInfo.getContainerInfo();
        if (containerId == null) {
            assertThat(containerInfo).isNull();
        } else {
            assertThat(containerInfo).describedAs("missing container info from '%s'", null).isNotNull(); // TODO
            assertThat(containerInfo.getId()).isEqualTo(containerId);
        }
        SystemInfo.Kubernetes kubernetesInfo = systemInfo.getKubernetesInfo();
        if (podId == null) {
            assertThat(kubernetesInfo).isNull();
        } else {
            assertThat(kubernetesInfo).describedAs("missing kubernetes info from '%s'", null).isNotNull(); // TODO
            assertThat(kubernetesInfo.getPod()).isNotNull();
            assertThat(kubernetesInfo.getPod().getUid()).isEqualTo(podId);
        }
    }

    static Stream<Arguments> getTestContainerMetadata() {
        JsonNode json = TestJsonSpec.getJson("container_metadata_discovery.json");

        assertThat(json.isObject())
            .describedAs("unexpected JSON spec format")
            .isTrue();

        List<Arguments> args = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> iterator = json.fields();
        while(iterator.hasNext()){
            Map.Entry<String, JsonNode> entry = iterator.next();

            Map<String, List<String>> files = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> filesIterator = entry.getValue().get("files").fields();
            while (filesIterator.hasNext()) {
                Map.Entry<String, JsonNode> fileEntry = filesIterator.next();
                List<String> fileLines = new ArrayList<>();
                for (int i = 0; i < fileEntry.getValue().size(); i++) {
                    fileLines.add(fileEntry.getValue().get(i).asText());
                }
                files.put(fileEntry.getKey(), fileLines);
            }


            args.add(Arguments.of(
                entry.getKey(),
                files,
                entry.getValue().get("containerId").asText(null),
                entry.getValue().get("podId").asText(null)
            ));
        }

        return args.stream();
    }

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
    void testCloudFoundryContainerIdParsing() {
        String validId = "70eb4ce5-a065-4401-6990-88ed";
        String validLinePrefix = "9:net_cls,net_prio:/garden/";
        assertContainerId(validLinePrefix + validId, validId);
        assertContainerInfoIsNull(validLinePrefix.substring(2) + validId);
        assertContainerInfoIsNull(validLinePrefix + validId.replace('a', 'g'));
        assertContainerInfoIsNull(validLinePrefix.substring(0, validLinePrefix.length() - 1) + validId);
        assertContainerInfoIsNull(validLinePrefix + validId.substring(0, validId.length() - 1));
        String uuid = validId.concat("abcd1234");
        assertContainerId(validLinePrefix + uuid, uuid);
        assertContainerInfoIsNull(validLinePrefix + validId.concat("/"));
        assertContainerId("5:blkio:/system.slice/garden.service/garden/" + validId, validId);
    }

    @Test
    void testFargateContainerIdParsing() {
        // 1:name=systemd:/ecs/03752a671e744971a862edcee6195646/03752a671e744971a862edcee6195646-4015103728
        String validId = "03752a671e744971a862edcee6195646-4015103728";
        String validLinePrefix = "1:name=systemd:/ecs/03752a671e744971a862edcee6195646/";
        assertContainerId(validLinePrefix + validId, validId);
        assertContainerInfoIsNull(validLinePrefix.substring(2) + validId);
        assertContainerInfoIsNull(validLinePrefix + validId.replace('a', 'g'));
        // the Fargate regex allows only digits in the second part (after the dash) and hexadecimal chars in the first part
        assertContainerInfoIsNull(validLinePrefix + validId.replace('2', 'a'));
        String idWithSecondPartOnlyDigits = validId.replace('6', 'a');
        assertContainerId(validLinePrefix + idWithSecondPartOnlyDigits, idWithSecondPartOnlyDigits);
        assertContainerInfoIsNull(validLinePrefix.substring(0, validLinePrefix.length() - 1) + validId);
        assertContainerInfoIsNull(validLinePrefix + validId.substring(0, validId.length() - 1));
        assertContainerInfoIsNull(validLinePrefix + validId.concat("/"));
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

        // 12:pids:/kubepods/kubepods/besteffort/pod0e886e9a-3879-45f9-b44d-86ef9df03224/244a65edefdffe31685c42317c9054e71dc1193048cf9459e2a4dd35cbc1dba4
        containerId = "244a65edefdffe31685c42317c9054e71dc1193048cf9459e2a4dd35cbc1dba4";
        podId = "0e886e9a-3879-45f9-b44d-86ef9df03224";
        line = "12:pids:/kubepods/kubepods/besteffort/pod" + podId + "/" + containerId;
        systemInfo = assertContainerId(line, containerId);
        assertKubernetesInfo(systemInfo, podId, "my-host", null, null);

        // 10:cpuset:/kubepods/pod5eadac96-ab58-11ea-b82b-0242ac110009/7fe41c8a2d1da09420117894f11dd91f6c3a44dfeb7d125dc594bd53468861df
        containerId = "7fe41c8a2d1da09420117894f11dd91f6c3a44dfeb7d125dc594bd53468861df";
        podId = "5eadac96-ab58-11ea-b82b-0242ac110009";
        line = "10:cpuset:/kubepods/pod" + podId + "/" + containerId;
        systemInfo = assertContainerId(line, containerId);
        assertKubernetesInfo(systemInfo, podId, "my-host", null, null);
    }

    @Test
    void testUbuntuCgroup() {
        String line = "1:name=systemd:/user.slice/user-1000.slice/user@1000.service/apps.slice/apps-org.gnome.Terminal" +
            ".slice/vte-spawn-75bc72bd-6642-4cf5-b62c-0674e11bfc84.scope";
        assertThat(createSystemInfo().parseCgroupsLine(line).getContainerInfo()).isNull();
    }

    @Test
    void testOpenshiftFormDisney() {
        String line = "9:freezer:/kubepods.slice/kubepods-pod22949dce_fd8b_11ea_8ede_98f2b32c645c.slice" +
            "/docker-b15a5bdedd2e7645c3be271364324321b908314e4c77857bbfd32a041148c07f.scope";
        SystemInfo systemInfo = assertContainerId(line, "b15a5bdedd2e7645c3be271364324321b908314e4c77857bbfd32a041148c07f");
        assertKubernetesInfo(systemInfo, "22949dce-fd8b-11ea-8ede-98f2b32c645c", "my-host", null, null);
    }

    @Test
    void testKubernetesInfo_podUid_with_underscores() {
        // In such cases- underscores should be replaced with hyphens in the pod UID
        String line = "1:name=systemd:/kubepods.slice/kubepods-burstable.slice/" +
            "kubepods-burstable-pod90d81341_92de_11e7_8cf2_507b9d4141fa.slice/" +
            "crio-2227daf62df6694645fee5df53c1f91271546a9560e8600a525690ae252b7f63.scope";
        SystemInfo systemInfo = assertContainerId(line, "2227daf62df6694645fee5df53c1f91271546a9560e8600a525690ae252b7f63");
        assertKubernetesInfo(systemInfo, "90d81341-92de-11e7-8cf2-507b9d4141fa", "my-host", null, null);
    }

    @Test
    void testKubernetesInfo_containerd_cri() {
        // In such cases- underscores should be replaced with hyphens in the pod UID
        String line = "1:name=systemd:/system.slice/containerd.service/kubepods-burstable-podff49d0be_16b7_4a49_bb9e_8ec1f1f4e27f.slice" +
            ":cri-containerd:0f99ad5f45163ed14ab8eaf92ed34bb4a631d007f8755a7d79be614bcb0df0ef";
        SystemInfo systemInfo = assertContainerId(line, "0f99ad5f45163ed14ab8eaf92ed34bb4a631d007f8755a7d79be614bcb0df0ef");
        assertKubernetesInfo(systemInfo, "ff49d0be-16b7-4a49-bb9e-8ec1f1f4e27f", "my-host", null, null);
    }

    @Test
    void testKubernetesDownwardApi() throws Exception {
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

        Map<String, String> mockedEnv = new HashMap<>();
        mockedEnv.put("KUBERNETES_NODE_NAME", nodeName);
        mockedEnv.put("KUBERNETES_POD_NAME", podName);
        mockedEnv.put("KUBERNETES_NAMESPACE", namespace);
        mockedEnv.put("KUBERNETES_POD_UID", podUid);
        runWithCustomEnvVariables(mockedEnv, systemInfo::findContainerDetails);
        assertKubernetesInfo(systemInfo, podUid, podName, nodeName, namespace);

        // test partial settings
        systemInfo = assertContainerId(line, containerId);
        assertKubernetesInfo(systemInfo, originalPodUid, hostName, null, null);
        mockedEnv.put("KUBERNETES_POD_NAME", null);
        mockedEnv.put("KUBERNETES_POD_UID", null);
        runWithCustomEnvVariables(mockedEnv, systemInfo::findContainerDetails);
        assertKubernetesInfo(systemInfo, originalPodUid, hostName, nodeName, namespace);
    }


    private SystemInfo createSystemInfo() {
        return new SystemInfo("arch", "my-host", null, "platform");
    }

    private SystemInfo assertContainerId(String line, String containerId) {
        SystemInfo systemInfo = createSystemInfo();
        assertThat(systemInfo.parseCgroupsLine(line).getContainerInfo()).isNotNull();
        //noinspection ConstantConditions
        assertThat(systemInfo.getContainerInfo().getId()).isEqualTo(containerId);
        return systemInfo;
    }

    private void assertContainerInfoIsNull(String line) {
        SystemInfo systemInfo = createSystemInfo();
        assertThat(systemInfo.parseCgroupsLine(line).getContainerInfo()).isNull();
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
