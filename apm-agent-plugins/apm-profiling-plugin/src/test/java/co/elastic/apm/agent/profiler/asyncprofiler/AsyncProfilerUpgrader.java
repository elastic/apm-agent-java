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
package co.elastic.apm.agent.profiler.asyncprofiler;

import one.profiler.AsyncProfiler;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test class is disabled by default. It is used as a utility for manually upgrading async profiler - update the
 * {@link #TARGET_VERSION} and run on a POSIX-compatible file system.
 */
@Disabled
public class AsyncProfilerUpgrader {

    static final String TARGET_VERSION = "1.8.7";
    static final String TAR_GZ_FILE_EXTENSION = ".tar.gz";
    static final String COMMON_BINARY_FILE_NAME = "libasyncProfiler.so";

    static final String[] USED_ARTIFACTS = {
        "linux-aarch64",
        "linux-arm",
        "linux-x64",
        "linux-x86",
        "macos-x64"
    };

    @Test
    void updateAsyncProfilerBinaries() throws Exception {
        GitHub github = GitHub.connectAnonymously();
        GHRepository repository = github.getRepository("jvm-profiling-tools/async-profiler");
        GHRelease release = repository.getReleaseByTagName("v" + TARGET_VERSION);
        PagedIterable<GHAsset> releaseAssets = release.listAssets();
        Path downloadDirPath = Files.createTempDirectory(String.format("AsyncProfiler_%s_", TARGET_VERSION));
        for (GHAsset releaseAsset : releaseAssets) {
            if (releaseAsset.getContentType().equals("application/x-gzip")) {
                downloadAndReplaceBinary(releaseAsset.getBrowserDownloadUrl(), releaseAsset.getName(), downloadDirPath, releaseAsset.getSize());
            }
        }

        // test we are now using the right version
        Path thisOsLib = getBinariesResourceDir().resolve(co.elastic.apm.agent.profiler.asyncprofiler.AsyncProfiler.getLibraryFileName() + ".so");
        AsyncProfiler asyncProfiler = AsyncProfiler.getInstance(thisOsLib.toString());
        assertThat(asyncProfiler.getVersion()).isEqualTo(TARGET_VERSION);
    }

    private void downloadAndReplaceBinary(String ghAssetDownloadUrl, String ghAssetName, Path targetDownloadDir, long expectedSize) throws Exception {
        String artifactNamePattern = null;
        for (String artifact : USED_ARTIFACTS) {
            if (ghAssetName.contains(artifact)) {
                artifactNamePattern = artifact;
                break;
            }
        }
        if (artifactNamePattern == null) {
            System.out.println(ghAssetName + " is not within the list of used artifacts, skipping download");
            return;
        }
        Path targetDownloadPath = targetDownloadDir.resolve(ghAssetName);
        System.out.println(String.format("Downloading from %s into %s and extracting binary", ghAssetName, targetDownloadDir));
        Path localBinaryPath = downloadAndExtractBinary(ghAssetDownloadUrl, targetDownloadPath, expectedSize);
        assertThat(localBinaryPath)
            .describedAs("Failed to download and extract binary file from " + ghAssetDownloadUrl)
            .isNotNull();
        System.out.println(String.format("Binary file for %s was extracted into %s", ghAssetName, localBinaryPath));
        replaceBinary(localBinaryPath, artifactNamePattern);
    }

    @Nullable
    private Path downloadAndExtractBinary(String ghAssetDownloadUrl, Path targetDownloadPath, long expectedSize) throws IOException {
        System.out.println("Downloading from " + ghAssetDownloadUrl);
        URLConnection assetUrlConnection = new URL(ghAssetDownloadUrl).openConnection();
        long actualSize;
        try (InputStream in = assetUrlConnection.getInputStream()) {
            actualSize = Files.copy(in, targetDownloadPath);
        }
        assertThat(actualSize).isEqualTo(expectedSize);
        return extractBinaryFileFromArchive(targetDownloadPath);
    }

    @Nullable
    private Path extractBinaryFileFromArchive(Path assetArchivePath) throws IOException {
        String archiveFileName = assetArchivePath.getFileName().toString();
        if (!archiveFileName.endsWith(TAR_GZ_FILE_EXTENSION)) {
            throw new IllegalArgumentException(String.format("Cannot extract %s - expecting a path to a %s file", archiveFileName, TAR_GZ_FILE_EXTENSION));
        }

        Path assetDirPath = assetArchivePath.getParent();
        if (!Files.exists(assetDirPath)) {
            Files.createDirectory(assetDirPath);
        }
        String extractedDirName = archiveFileName.substring(0, archiveFileName.length() - TAR_GZ_FILE_EXTENSION.length());
        Path extractedDirPath = assetDirPath.resolve(extractedDirName);
        if (!Files.exists(extractedDirPath)) {
            Files.createDirectory(extractedDirPath);
        }

        Path binaryFilePath = null;
        try (InputStream fis = Files.newInputStream(assetArchivePath);
             GZIPInputStream gis = new GZIPInputStream(fis);
             ArchiveInputStream ais = new TarArchiveInputStream(gis)) {

            ArchiveEntry entry;
            while ((entry = ais.getNextEntry()) != null) {
                if (!ais.canReadEntryData(entry)) {
                    throw new IllegalStateException("Cannot read an archive entry - " + entry.getName());
                }
                if (entry.getName().endsWith(COMMON_BINARY_FILE_NAME)) {
                    Path filePath = extractedDirPath.resolve(COMMON_BINARY_FILE_NAME);
                    Files.copy(ais, filePath);
                    binaryFilePath = filePath;
                }
            }
        }
        return binaryFilePath;
    }

    /**
     * Replaces an existing binary file with its downloaded counterpart.
     * <p>
     *     NOTE: when replacing the existing binary file, this method attempts to apply the current binary file's
     *     permissions to the one replacing it, assuming the underlying file system is POSIX-compatible. If this is
     *     not the case, and error will occur
     * </p>
     *
     * @param downloadedArtifact the path to the downloaded binary file
     * @param artifactName the name of the artifact to replace, see {@link #USED_ARTIFACTS}
     * @throws Exception thrown when an error occurs while trying to replace, or when running on non POSIX file system
     */
    private void replaceBinary(Path downloadedArtifact, String artifactName) throws Exception {
        if (!downloadedArtifact.toString().contains(artifactName)) {
            throw new IllegalArgumentException(String.format("the provided path for the downloaded artifact [%s] must " +
                "be of a file containing the provided artifact name: %s", downloadedArtifact, artifactName));
        }

        Path binariesResourceDir = getBinariesResourceDir();
        String binaryResourceName = String.format("libasyncProfiler-%s.so", artifactName);
        Path binaryResourcePath = binariesResourceDir.resolve(binaryResourceName);
        if (!Files.exists(binaryResourcePath)) {
            throw new IllegalStateException(String.format("Expected binary file does not exist: %s", binaryResourcePath.toString()));
        }
        System.out.println(String.format("Replacing %s with %s", binaryResourcePath, downloadedArtifact));
        Set<PosixFilePermission> posixFilePermissions = Files.getPosixFilePermissions(binaryResourcePath);
        Files.move(downloadedArtifact, binaryResourcePath, StandardCopyOption.REPLACE_EXISTING);
        Files.setPosixFilePermissions(binaryResourcePath, posixFilePermissions);
    }

    private Path getBinariesResourceDir() throws URISyntaxException {
        // <agent-repo-root>/apm-agent-plugins/apm-profiling-plugin/target/classes/asyncprofiler
        Path asyncProfilerTestResourcePath = Paths.get(AsyncProfilerUpgrader.class.getResource("/asyncprofiler").toURI());
        // <agent-repo-root>/apm-agent-plugins/apm-profiling-plugin/target/classes/asyncprofiler
        Path pluginRootDir = asyncProfilerTestResourcePath.getParent().getParent().getParent();
        // We are looking for // <agent-repo-root>/apm-agent-plugins/apm-profiling-plugin/src/main/resources/asyncprofiler
        return pluginRootDir.resolve("src").resolve("main").resolve("resources").resolve("asyncprofiler");
    }
}
