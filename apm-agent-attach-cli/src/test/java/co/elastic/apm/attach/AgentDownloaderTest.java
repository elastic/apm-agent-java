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

import co.elastic.apm.attach.bouncycastle.BouncyCastleVerifier;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class AgentDownloaderTest {

    private static final String TEST_KEY_ID = "90AD76CD56AA73A9";

    private final AgentDownloader agentDownloader = new AgentDownloader(new BouncyCastleVerifier());

    @Test
    void testMavenUrl() throws Exception {
        String mavenUrl = agentDownloader.getAgentMavenBaseUrl("1.24.0");
        assertThat(mavenUrl).isNotNull();
        assertThat(mavenUrl).isEqualTo("https://repo1.maven.org/maven2/co/elastic/apm/elastic-apm-agent/1.24.0");
    }

    @Test
    void testWrongVersion() {
        assertThatThrownBy(() -> agentDownloader.getAgentMavenBaseUrl("1.24.1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> agentDownloader.getAgentMavenBaseUrl("error")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDownloadFile() throws Exception {
        String agentVersion = "1.25.0";
        String mavenAgentBaseUrl = agentDownloader.getAgentMavenBaseUrl(agentVersion);
        String agentPgpSignatureFileName = String.format(Locale.ROOT, "%s.asc", agentDownloader.computeAgentJarName(agentVersion));
        String mavenPgpSignatureUrl = agentDownloader.computeFileUrl(mavenAgentBaseUrl, agentPgpSignatureFileName);
        System.out.println("mavenPgpSignatureUrl = " + mavenPgpSignatureUrl);
        Path targetDir = Utils.getTargetAgentDir(agentVersion);
        String targetFileName = "test.asc";
        final Path localFilePath = targetDir.resolve(targetFileName);
        Files.deleteIfExists(localFilePath);
        agentDownloader.downloadFile(mavenPgpSignatureUrl, localFilePath);
        assertThat(Files.isReadable(localFilePath)).isTrue();
        assertThatThrownBy(() -> agentDownloader.downloadFile(mavenPgpSignatureUrl, localFilePath)).isInstanceOf(FileAlreadyExistsException.class);
    }

    // disabled due to overhead, but very convenient for sanity testing
    @Disabled
    @Test
    void testDownloadAndVerifyAgent() throws Exception {
        String agentVersion = "1.25.0";
        Path targetDir = Utils.getTargetAgentDir(agentVersion);
        final Path localAgentPath = targetDir.resolve(agentDownloader.computeAgentJarName(agentVersion));
        System.out.println("localAgentPath = " + localAgentPath);
        Files.deleteIfExists(localAgentPath);
        assertThat(Files.exists(localAgentPath)).isFalse();
        agentDownloader.downloadAndVerifyAgent(agentVersion);
        assertThat(Files.isReadable(localAgentPath)).isTrue();
    }

    // disabled due to overhead, but very convenient for sanity testing
    @Disabled
    @Test
    void testDownloadAgentAndFailVerification() throws Exception {
        AgentDownloader spyAgentDownloader = spy(new AgentDownloader(new BouncyCastleVerifier()));
        doReturn(TEST_KEY_ID).when(spyAgentDownloader).getPublicKeyId();
        String agentVersion = "1.24.0";
        Path targetDir = Utils.getTargetAgentDir(agentVersion);
        final Path localAgentPath = targetDir.resolve(spyAgentDownloader.computeAgentJarName(agentVersion));
        System.out.println("localAgentPath = " + localAgentPath);
        Files.deleteIfExists(localAgentPath);
        assertThat(Files.exists(localAgentPath)).isFalse();
        assertThatThrownBy(() -> spyAgentDownloader.downloadAndVerifyAgent(agentVersion)).isInstanceOf(IllegalStateException.class);
        assertThat(Files.exists(localAgentPath)).isFalse();
    }
}
