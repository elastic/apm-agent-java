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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ResourceExtractionUtil {

    /**
     * Extracts a classpath resource to {@code ${System.getProperty("java.io.tmpdir")}/$prefix-$hash.$suffix}.
     * If the file has already been extracted it will not be extracted again.
     *
     * @param resource       The classpath resource to extract.
     * @param prefix         The prefix of the extracted file.
     * @param suffix         The suffix of the extracted file.
     * @return the extracted file.
     */
    public static synchronized File extractResourceToTempDirectory(String resource, String prefix, String suffix) {
        return extractResourceToDirectory(resource, prefix, suffix, System.getProperty("java.io.tmpdir"));
    }

    /**
     * Extracts a classpath resource to {@code $directory/$prefix-$userHash-$hash.$suffix}.
     * If the file has already been extracted it will not be extracted again.
     *
     * @param resource       The classpath resource to extract.
     * @param prefix         The prefix of the extracted file.
     * @param suffix         The suffix of the extracted file.
     * @param directory      The directory in which the file is to be created, or null if the default temporary-file directory is to be used.
     * @return the extracted file.
     */
    /*
     * Why it's synchronized : if the same JVM try to lock file, we got an java.nio.channels.OverlappingFileLockException.
     * So we need to block until the file is totally written.
     */
    public static synchronized File extractResourceToDirectory(String resource, String prefix, String suffix, String directory) {
        try (InputStream resourceStream = ResourceExtractionUtil.class.getResourceAsStream("/" + resource)) {
            if (resourceStream == null) {
                throw new IllegalStateException(resource + " not found");
            }
            String userHash = "";
            if (System.getProperties().contains("user.name")) {
                // we have to include current user name as multiple copies of the same agent could be attached
                // to multiple JVMs, each running under a different user. Also, we have to make it path-friendly.
                userHash = md5Hash(System.getProperty("user.name"));
                userHash += "-";
            }
            String resourceHash = md5Hash(ResourceExtractionUtil.class.getResourceAsStream("/" + resource));

            File tempFile = new File(directory, prefix + "-" + userHash + resourceHash + suffix);
            if (!tempFile.exists()) {
                try (FileOutputStream out = new FileOutputStream(tempFile)) {
                    FileChannel channel = out.getChannel();
                    // If multiple JVM start on same compute, they can write in same file
                    // and this file will be corrupted.
                    try (FileLock ignored = channel.lock()) {
                        if (tempFile.length() == 0) {
                            byte[] buffer = new byte[1024];
                            for (int length; (length = resourceStream.read(buffer)) != -1; ) {
                                out.write(buffer, 0, length);
                            }
                        }
                    }
                }
            } else if (!md5Hash(new FileInputStream(tempFile)).equals(resourceHash)) {
                throw new IllegalStateException("Invalid MD5 checksum of " + tempFile + ". Please delete this file.");
            }
            return tempFile;
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String md5Hash(InputStream resourceAsStream) throws IOException, NoSuchAlgorithmException {
        try (InputStream is = resourceAsStream) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[1024];
            DigestInputStream dis = new DigestInputStream(is, md);
            while (dis.read(buffer) != -1) {}
            return String.format("%032x", new BigInteger(1, md.digest()));
        }
    }

    private static String md5Hash(String s) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(s.getBytes());
        return String.format("%032x", new BigInteger(1, md.digest()));
    }
}
