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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility for downloading any given version of the Elastic APM Java agent from maven central.
 * After being downloaded, the agent jar is verified to match the expected PGP signature from maven central. If
 * verification fails, the agent is removed from the local storage and the entire task fails.
 */
public class AgentDownloader {

    private static final Pattern VERSION_EXTRACTION_REGEX = Pattern.compile("<version>(.+?)</version>");
    private static final String AGENT_GROUP_ID = "co.elastic.apm";
    private static final String AGENT_ARTIFACT_ID = "elastic-apm-agent";
    private static final String CLI_JAR_VERSION;
    public static final String USER_AGENT;

    private final Map<String,byte[]> keys;

    static {
        CLI_JAR_VERSION = readCliJarVersion();
        StringBuilder userAgent = new StringBuilder("elastic-apm-agent-java-attach-cli");
        if (CLI_JAR_VERSION != null) {
            userAgent.append("/").append(CLI_JAR_VERSION);
        }
        USER_AGENT = userAgent.toString();
    }

    // intentionally not static so that we create the logger only after proper initialization
    private final Logger logger = LogManager.getLogger(AgentDownloader.class);

    private static String readCliJarVersion() {
        String pomPropertiesLocation = "/META-INF/maven/" + AGENT_GROUP_ID + "/" + "apm-agent-attach-cli" + "/pom.properties";
        Properties pomProperties = null;
        try (InputStream pomPropertiesStream = AgentDownloader.class.getResourceAsStream(pomPropertiesLocation)) {
            if (pomPropertiesStream != null) {
                pomProperties = new Properties();
                pomProperties.load(pomPropertiesStream);
            }
        } catch (Exception exception) {
            pomProperties = null;
        }
        if (pomProperties != null) {
            return pomProperties.getProperty("version");
        }
        return null;
    }

    public static String getCliJarVersion() {
        return CLI_JAR_VERSION;
    }

    public AgentDownloader(PgpSignatureVerifier pgpSignatureVerifier) {
        this.pgpSignatureVerifier = pgpSignatureVerifier;
        this.keys = new HashMap<String, byte[]>();
        keys.put("D27D666CD88E42B4", getPubKeyContent("/pub_key_D27D666CD88E42B4.asc", 1780));
        keys.put("8AB554FD8F207067", getPubKeyContent("/pub_key_8AB554FD8F207067.asc", 977));
    }

    private final PgpSignatureVerifier pgpSignatureVerifier;

    /**
     * Downloads the agent jar, authenticates and verifies its PGP signature and returns the path for the locally
     * stored jar.
     *
     * @param agentVersion the target agent version
     * @return the path for the locally stored agent jar
     * @throws Exception failure either with downloading, copying to local file system, or in PGP signature verification
     */
    Path downloadAndVerifyAgent(String agentVersion) throws Exception {
        logger.debug("Requested to download Elastic APM Java agent version {}", agentVersion);
        final String mavenAgentBaseUrl = getAgentMavenVersionBaseUrl(agentVersion);
        Path targetDir = AgentDownloadUtils.of(agentVersion).getTargetAgentDir();
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
    String getAgentMavenVersionBaseUrl(String agentVersion) throws Exception {
        final String agentMavenUrl = String.format("%s/%s", getAgentArtifactMavenBaseUrl(), agentVersion);
        if (!verifyUrl(agentMavenUrl)) {
            throw new IllegalArgumentException(String.format("Cannot find maven URL for version %s, make sure provided version is valid", agentVersion));
        }
        return agentMavenUrl;
    }

    /**
     * Returns the url for the Elastic APM Agent jar artifact in maven.
     */
    static String getAgentArtifactMavenBaseUrl() throws Exception {
        final String groupId = AGENT_GROUP_ID.replace(".", "/");
        final String agentMavenUrl = String.format("https://repo1.maven.org/maven2/%s/%s", groupId, AGENT_ARTIFACT_ID);
        if (!verifyUrl(agentMavenUrl)) {
            throw new IllegalArgumentException(String.format("Cannot find maven URL for agent artifact: %s:%s", groupId, AGENT_ARTIFACT_ID));
        }
        return agentMavenUrl;
    }

    /**
     * Returns {@code true} if the given url exists, and {@code false} otherwise.
     * <p>
     * The given url must be {@code https} and existing means a {@code HEAD} request returns 200.
     */
    private static boolean verifyUrl(String urlString) throws IOException {
        HttpURLConnection urlConnection = openConnection(urlString);
        urlConnection.setRequestMethod("HEAD");
        urlConnection.connect();
        return urlConnection.getResponseCode() == 200;
    }

    private static HttpURLConnection openConnection(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.addRequestProperty("User-Agent", USER_AGENT);
        return urlConnection;
    }

    /**
     * Downloads a file from the provided URL into the provided local file path
     *
     * @param remoteFileUrlString remote file URL as string
     * @param localFilePath       destination path for the dewnloaded file
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
     * @throws Exception if an I/O exception occurs reading from various input streams
     */
    void verifyPgpSignature(final Path agentJarFile, final String mavenAgentUrlString) throws Exception {
        final String ascUrlString = mavenAgentUrlString + ".asc";
        logger.info("Verifying Elastic APM Java Agent jar PGP signature...");

        HttpURLConnection signatureFileUrlConnection = openConnection(ascUrlString);
        int signatureLength = signatureFileUrlConnection.getContentLength();
        if (signatureLength <= 0) {
            throw new IllegalStateException("unexpected signature size");
        }

        byte[] signatureBytes;
        try (InputStream inputStream = signatureFileUrlConnection.getInputStream()) {
            signatureBytes = toByteArray(inputStream, signatureLength);
        }

        for (Map.Entry<String, byte[]> entry : getPublicKeys().entrySet()) {
            try (
                InputStream agentJarIS = Files.newInputStream(agentJarFile);
            ) {
                InputStream pgpSignatureIS = new ByteArrayInputStream(signatureBytes);
                logger.debug("attempt to verify with key [{}]", entry.getKey());
                InputStream publicKeyIS = new ByteArrayInputStream(entry.getValue());
                try {
                    if (pgpSignatureVerifier.verifyPgpSignature(agentJarIS, pgpSignatureIS, publicKeyIS, entry.getKey())) {
                        logger.info("Elastic APM Java Agent jar PGP signature successfully verified.");
                        return;
                    } else {
                        logger.debug("key verification failed with key [{}]", entry.getKey());
                    }
                } catch (Exception e) {
                    logger.debug(e.getMessage());
                }
            }

        }
        throw new IllegalStateException("Signature verification for " + mavenAgentUrlString + " failed, downloaded jar may have been tampered with.");



    }

    static String findLatestVersion() throws Exception {
        String agentArtifactMavenMetadatUrl = getAgentArtifactMavenBaseUrl() + "/maven-metadata.xml";
        HttpURLConnection httpURLConnection = openConnection(agentArtifactMavenMetadatUrl);
        TreeSet<Version> versions = parseMavenMetadataXml(httpURLConnection.getInputStream());
        if (versions.isEmpty()) {
            throw new IllegalStateException("Failed to parse agent versions from the contents of " + agentArtifactMavenMetadatUrl);
        }
        return versions.last().toString();
    }

    static TreeSet<Version> parseMavenMetadataXml(InputStream htmlInputStream) throws IOException {
        TreeSet<Version> versions = new TreeSet<>();
        BufferedReader versionsHtmlReader = new BufferedReader(new InputStreamReader(htmlInputStream));
        String line;
        while ((line = versionsHtmlReader.readLine()) != null) {
            try {
                Matcher matcher = VERSION_EXTRACTION_REGEX.matcher(line);
                if (matcher.find()) {
                    Version version = Version.of(matcher.group(1));
                    if (!version.hasSuffix()) {
                        versions.add(version);
                    }
                }
            } catch (Exception e) {
                // ignore, probably a regex false positive
            }
        }
        return versions;
    }

    /**
     * Returns the public keys used to sign agent artifacts
     *
     * @return map of signing keys, with key ID as key, and the raw public key value as value
     */
    public Map<String, byte[]> getPublicKeys() {
        return keys;
    }

    private static byte[] getPubKeyContent(String path, int size) {
        try (InputStream inputStream = AgentDownloader.class.getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IllegalStateException("unknown key file: " + path);
            }
            return toByteArray(inputStream, size);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] toByteArray(InputStream inputStream, int size) throws IOException {
        byte[] result = new byte[size];
        if (size == 0 || size != inputStream.read(result) || inputStream.read() >= 0) {
            throw new IllegalStateException("invalid input size" + size);
        }
        return result;
    }

}
