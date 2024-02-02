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
package co.elastic.apm.attach;

import co.elastic.apm.agent.common.util.Version;
import co.elastic.apm.attach.bouncycastle.BouncyCastleVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class AgentDownloaderTest {

    // valid key stored in valid_key.asc, but not the one used to sign agent artifacts
    private static final String TEST_KEY_ID = "90AD76CD56AA73A9";

    private final AgentDownloader agentDownloader = new AgentDownloader(new BouncyCastleVerifier());

    @Test
    void testMavenUrl() throws Exception {
        String mavenUrl = agentDownloader.getAgentMavenVersionBaseUrl("1.24.0");
        assertThat(mavenUrl).isNotNull();
        assertThat(mavenUrl).isEqualTo("https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent/1.24.0");
    }

    @Test
    void testWrongVersion() {
        assertThatThrownBy(() -> agentDownloader.getAgentMavenVersionBaseUrl("1.24.1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> agentDownloader.getAgentMavenVersionBaseUrl("error")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDownloadFile() throws Exception {
        String agentVersion = "1.25.0";
        String mavenAgentBaseUrl = agentDownloader.getAgentMavenVersionBaseUrl(agentVersion);
        String agentPgpSignatureFileName = String.format(Locale.ROOT, "%s.asc", agentDownloader.computeAgentJarName(agentVersion));
        String mavenPgpSignatureUrl = agentDownloader.computeFileUrl(mavenAgentBaseUrl, agentPgpSignatureFileName);
        System.out.println("mavenPgpSignatureUrl = " + mavenPgpSignatureUrl);
        Path targetDir = AgentDownloadUtils.of(agentVersion).getTargetAgentDir();
        String targetFileName = "test.asc";
        final Path localFilePath = targetDir.resolve(targetFileName);
        Files.deleteIfExists(localFilePath);
        agentDownloader.downloadFile(mavenPgpSignatureUrl, localFilePath);
        assertThat(Files.isReadable(localFilePath)).isTrue();
        assertThatThrownBy(() -> agentDownloader.downloadFile(mavenPgpSignatureUrl, localFilePath)).isInstanceOf(FileAlreadyExistsException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.24.0", "1.46.0", "latest"})
    void testDownloadAndVerifyAgent(String agentVersion) throws Exception {
        if ("latest".equals(agentVersion)) {
            agentVersion = AgentDownloader.findLatestVersion();
        }
        Path targetDir = AgentDownloadUtils.of(agentVersion).getTargetAgentDir();
        final Path localAgentPath = targetDir.resolve(agentDownloader.computeAgentJarName(agentVersion));
        System.out.println("localAgentPath = " + localAgentPath);
        Files.deleteIfExists(localAgentPath);
        assertThat(Files.exists(localAgentPath)).isFalse();
        agentDownloader.downloadAndVerifyAgent(agentVersion);
        assertThat(Files.isReadable(localAgentPath)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.24.0", "1.46.0"})
    void testDownloadAgentAndFailVerification(String agentVersion) throws Exception {
        AgentDownloader spyAgentDownloader = spy(new AgentDownloader(new BouncyCastleVerifier()));

        // using invalid key ID but valid key value
        byte[] key;
        try (InputStream is = Objects.requireNonNull(AgentDownloaderTest.class.getResourceAsStream("/valid_key.asc"))) {
            key = is.readAllBytes();
        }
        doReturn(Map.of(TEST_KEY_ID, key)).when(spyAgentDownloader).getPublicKeys();

        Path targetDir = AgentDownloadUtils.of(agentVersion).getTargetAgentDir();
        final Path localAgentPath = targetDir.resolve(spyAgentDownloader.computeAgentJarName(agentVersion));
        System.out.println("localAgentPath = " + localAgentPath);
        Files.deleteIfExists(localAgentPath);
        assertThat(Files.exists(localAgentPath)).isFalse();
        assertThatThrownBy(() -> spyAgentDownloader.downloadAndVerifyAgent(agentVersion)).isInstanceOf(IllegalStateException.class);
        assertThat(Files.exists(localAgentPath)).isFalse();
    }

    @Test
    void testLatestVersion() throws Exception {
        String latestVersion = AgentDownloader.findLatestVersion();
        System.out.println("latestVersion = " + latestVersion);
        assertThat(latestVersion).isNotEmpty();
    }

    @Test
    void testAgentArtifactMavenPageParsing() throws IOException {
        TreeSet<Version> versions = AgentDownloader.parseMavenMetadataXml(AgentDownloaderTest.class.getResourceAsStream(
            "/maven-metadata.xml"));
        assertThat(versions).hasSize(50);
        assertThat(versions.first().toString()).isEqualTo("0.5.1");
        assertThat(versions.last().toString()).isEqualTo("1.31.0");
        assertThat(versions).doesNotContain(Version.of("1.0.0.RC1"));
    }
}
