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
package co.elastic.apm.agent.common.util;

import co.elastic.apm.agent.common.JvmRuntimeInfo;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

public class ResourceExtractionUtil {

    /**
     * Extracts a classpath resource to {@code ${System.getProperty("java.io.tmpdir")}/$prefix-$hash.$suffix}.
     * If the file has already been extracted it will not be extracted again.
     *
     * @param resource The classpath resource to extract.
     * @param prefix   The prefix of the extracted file.
     * @param suffix   The suffix of the extracted file.
     * @return the extracted file.
     */
    public static synchronized Path extractResourceToTempDirectory(String resource, String prefix, String suffix) {
        return extractResourceToDirectory(resource, prefix, suffix, Paths.get(System.getProperty("java.io.tmpdir")));
    }

    /**
     * Extracts a classpath resource to {@code $directory/$prefix-$userHash-$hash.$suffix}.
     * If the file has already been extracted it will not be extracted again.
     *
     * @param resource  The classpath resource to extract.
     * @param prefix    The prefix of the extracted file.
     * @param suffix    The suffix of the extracted file.
     * @param directory The directory in which the file is to be created, or null if the default temporary-file directory is to be used.
     * @return the extracted file.
     */
    /*
     * Why it's synchronized : if the same JVM try to lock file, we got an java.nio.channels.OverlappingFileLockException.
     * So we need to block until the file is totally written.
     */
    public static synchronized Path extractResourceToDirectory(String resource, String prefix, String suffix, Path directory) {
        try (InputStream resourceStream = ResourceExtractionUtil.class.getResourceAsStream("/" + resource)) {
            if (resourceStream == null) {
                throw new IllegalStateException(resource + " not found");
            }
            UserPrincipal currentUserPrincipal = getCurrentUserPrincipal();
            // we have to include current user name as multiple copies of the same agent could be attached
            // to multiple JVMs, each running under a different user. Hashing makes the name path-friendly.
            String userHash = hash(currentUserPrincipal.getName());
            // to guard against re-using previous versions
            String resourceHash = hash(ResourceExtractionUtil.class.getResourceAsStream("/" + resource));

            Path tempFile = directory.resolve(prefix + "-" + userHash.substring(0, 32) + "-" + resourceHash.substring(0, 32) + suffix);
            try {
                FileAttribute<?>[] attr;
                if (tempFile.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                    attr = new FileAttribute[]{PosixFilePermissions.asFileAttribute(EnumSet.of(OWNER_WRITE, OWNER_READ))};
                } else {
                    attr = new FileAttribute[0];
                }
                try (FileChannel channel = FileChannel.open(tempFile, EnumSet.of(CREATE_NEW, WRITE), attr)) {
                    // make other JVM instances wait until fully written
                    try (FileLock writeLock = channel.lock()) {
                        channel.transferFrom(Channels.newChannel(resourceStream), 0, Long.MAX_VALUE);
                    }
                }
            } catch (FileAlreadyExistsException e) {
                JvmRuntimeInfo jvmRuntimeInfo = JvmRuntimeInfo.ofCurrentVM();
                try (FileChannel channel = (jvmRuntimeInfo.isZos() || jvmRuntimeInfo.isOs400()) ?
                    FileChannel.open(tempFile, READ) :
                    FileChannel.open(tempFile, READ, NOFOLLOW_LINKS)) {
                    // wait until other JVM instances have fully written the file
                    // multiple JVMs can read the file at the same time
                    try (FileLock readLock = channel.lock(0, Long.MAX_VALUE, true)) {
                        if (!hash(Files.newInputStream(tempFile)).equals(resourceHash)) {
                            throw new IllegalStateException("Invalid checksum of " + tempFile + ". Please delete this file.");
                        } else if (!Files.getOwner(tempFile).equals(currentUserPrincipal)) {
                            throw new IllegalStateException("File " + tempFile + " is not owned by '" + currentUserPrincipal.getName() + "'. Please delete this file.");
                        }
                    }
                }
            }
            return tempFile.toAbsolutePath();
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static UserPrincipal getCurrentUserPrincipal() throws IOException {
        Path whoami = Files.createTempFile("whoami", ".tmp");
        try {
            return Files.getOwner(whoami);
        } finally {
            Files.delete(whoami);
        }
    }

    private static String hash(InputStream resourceAsStream) throws IOException, NoSuchAlgorithmException {
        try (InputStream is = resourceAsStream) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024];
            DigestInputStream dis = new DigestInputStream(is, md);
            while (dis.read(buffer) != -1) {}
            return new BigInteger(1, md.digest()).toString(16);
        }
    }

    private static String hash(String s) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(s.getBytes());
        return new BigInteger(1, md.digest()).toString(16);
    }
}
