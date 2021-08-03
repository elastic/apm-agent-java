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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static co.elastic.apm.attach.Utils.getTargetAgentDir;

/**
 * A utility for downloading any given version of the Elastic APM Java agent from maven central.
 * After being downloaded, the agent jar is verified to match the expected PGP signature from maven central. If
 * verification fails, the agent is removed from the local storage and the entire task fails.
 */
public class AgentDownloader {

    private static final String AGENT_GROUP_ID = "co.elastic.apm";
    private static final String AGENT_ARTIFACT_ID = "elastic-apm-agent";

    // intentionally not static so that we create the logger only after proper initialization
    private final Logger logger = LogManager.getLogger(AgentDownloader.class);

    public AgentDownloader(PgpSignatureVerifier pgpSignatureVerifier) {
        this.pgpSignatureVerifier = pgpSignatureVerifier;
    }

    private final PgpSignatureVerifier pgpSignatureVerifier;

    /**
     * Downloads the agent jar, authenticates and verifies its PGP signature and returns the path for the locally
     * stored jar.
     * @param agentVersion the target agent version
     * @return the path for the locally stored agent jar
     * @throws Exception failure either with downloading, copying to local file system, or in PGP signature verification
     */
    Path downloadAndVerifyAgent(String agentVersion) throws Exception {
        logger.debug("Requested to download Elastic APM Java agent version {}", agentVersion);
        final String mavenAgentBaseUrl = getAgentMavenBaseUrl(agentVersion);
        Path targetDir = getTargetAgentDir(agentVersion);
        String agentJarName = computeAgentJarName(agentVersion);
        String mavenAgentJarUrl = computeFileUrl(mavenAgentBaseUrl, agentJarName);
        Path localAgentJarPath = targetDir.resolve(agentJarName);
        boolean downloadAndVerify = true;

        if (Files.exists(localAgentJarPath)) {
            logger.debug("{} already exists at {}", agentJarName, targetDir);
            if (Files.isReadable(localAgentJarPath)) {
                downloadAndVerify = false;
            } else {
                // quick and dirty - this is a temporary solution until we download agent from the Fleet package registry
                logger.info("{} is not readable for the current user, removing and downloading again", localAgentJarPath);
                Files.delete(localAgentJarPath);
            }
        }

        if (downloadAndVerify) {
            downloadFile(mavenAgentJarUrl, localAgentJarPath);
            try {
                verifyPgpSignature(localAgentJarPath, mavenAgentJarUrl);
            } catch (Throwable throwable) {
                if (Files.exists(localAgentJarPath)) {
                    Files.delete(localAgentJarPath);
                }
                throw throwable;
            }
        }
        return localAgentJarPath;
    }

    String computeFileUrl(String mavenArtifactBaseUrl, String artifactFileName) {
        return String.format(Locale.ROOT, "%s/%s", mavenArtifactBaseUrl, artifactFileName);
    }

    String computeAgentJarName(String agentVersion) {
        return String.format(Locale.ROOT, "%s-%s.jar", AGENT_ARTIFACT_ID, agentVersion);
    }

    /**
     * Returns the url for an Elastic APM Agent jar in maven.
     */
    String getAgentMavenBaseUrl(String agentVersion) throws Exception {
        final String groupId = AGENT_GROUP_ID.replace(".", "/");
        final String agentMavenUrl = String.format(Locale.ROOT, "https://repo1.maven.org/maven2/%s/%s/%s", groupId, AGENT_ARTIFACT_ID, agentVersion);
        if (!verifyUrl(agentMavenUrl)) {
            throw new IllegalArgumentException(String.format("Cannot find maven URL for version %s, make sure provided version is valid", agentVersion));
        }
        return agentMavenUrl;
    }

    /**
     * Returns {@code true} if the given url exists, and {@code false} otherwise.
     * <p>
     * The given url must be {@code https} and existing means a {@code HEAD} request returns 200.
     */
    private boolean verifyUrl(String urlString) throws IOException {
        HttpURLConnection urlConnection = openConnection(urlString);
        urlConnection.setRequestMethod("HEAD");
        urlConnection.connect();
        return urlConnection.getResponseCode() == 200;
    }

    private HttpURLConnection openConnection(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.addRequestProperty("User-Agent", "elastic-apm-agent-java-attach-cli");
        return urlConnection;
    }

    /**
     * Downloads a file from the provided URL into the provided local file path
     * @param remoteFileUrlString remote file URL as string
     * @param localFilePath destination path for the dewnloaded file
     * @throws IOException indicating a failure during class reading or writing, or the file already exists
     */
    void downloadFile(String remoteFileUrlString, Path localFilePath) throws IOException {
        logger.info("Downloading file from {} to {}, this may take a few seconds...", remoteFileUrlString, localFilePath);
        HttpURLConnection jarConnection = openConnection(remoteFileUrlString);
        try (InputStream in = jarConnection.getInputStream()) {
            Files.copy(in, localFilePath);
        }
        logger.info("File download completed successfully.");
    }

    /**
     * Verify the signature of the downloaded agent jar.
     * The signature is obtained from maven based on the provided agent base URL.
     *
     * @param agentJarFile        the path to the downloaded agent jar
     * @param mavenAgentUrlString the maven base URL for the agent
     * @throws Exception  if an I/O exception occurs reading from various input streams
     */
    void verifyPgpSignature(final Path agentJarFile, final String mavenAgentUrlString) throws Exception {
        final String ascUrlString = mavenAgentUrlString + ".asc";
        logger.info("Verifying Elastic APM Java Agent jar PGP signature...");
        HttpURLConnection signatureFileUrlConnection = openConnection(ascUrlString);
        try (
            InputStream agentJarIS = Files.newInputStream(agentJarFile);
            InputStream pgpSignatureIS = signatureFileUrlConnection.getInputStream();
            InputStream publicKeyIS = getPublicKey()
        ) {
            if (!pgpSignatureVerifier.verifyPgpSignature(agentJarIS, pgpSignatureIS, publicKeyIS, getPublicKeyId())) {
                throw new IllegalStateException("Signature verification for " + mavenAgentUrlString +
                    " failed, downloaded jar may be tampered with.");
            }
        }
        logger.info("Elastic APM Java Agent jar PGP signature successfully verified.");
    }

    /**
     * Return the public key ID of our agent signing key.
     *
     * @return the public key ID
     */
    String getPublicKeyId() {
        return "D27D666CD88E42B4";
    }

    /**
     * An input stream to the public key of the signing key.
     *
     * @return an input stream to the public key
     */
    InputStream getPublicKey() {
        return AgentDownloader.class.getResourceAsStream("/public_key.asc");
    }
}
