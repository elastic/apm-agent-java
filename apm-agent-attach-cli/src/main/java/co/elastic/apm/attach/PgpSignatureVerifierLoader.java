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
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A dedicated class loader for the PGP signature verifier implementation and its dependencies.
 */
public class PgpSignatureVerifierLoader extends URLClassLoader {

    private final String verifierImplementationName;

    /**
     * Copies all required dependencies from a source directory to a target directory if they are not there already, or
     * if they are there but not readable for the current user, and creates a class loader that can load the provided
     * {@link PgpSignatureVerifier} implementation.
     * @param sourceLib             a path for a directory containing the {@link PgpSignatureVerifier} implementation
     *                              and all its dependencies. The source directory can be within a jar file or not.
     * @param targetLib             a path of the target directory in which all dependencies are to be copied to
     * @param verifierClassName     the fully class name of the {@link PgpSignatureVerifier} implementation
     * @return                      a {@link PgpSignatureVerifierLoader} instance
     * @throws Exception            error related to the {@link PgpSignatureVerifierLoader} instance setup, not to class loading
     */
    static PgpSignatureVerifierLoader getInstance(final String sourceLib, final Path targetLib, String verifierClassName) throws Exception {

        // done lazily so that we create the logger only after proper initialization
        final Logger logger = LogManager.getLogger(PgpSignatureVerifierLoader.class);

        Path libPath;
        // first try to see if the lib is available as a resource available to the current class loader
        URL resourceUrl = PgpSignatureVerifierLoader.class.getResource(sourceLib);
        if (resourceUrl != null) {
            URI libUri = resourceUrl.toURI();
            if (libUri.getScheme().equals("jar")) {
                FileSystem fileSystem;
                try {
                    fileSystem = FileSystems.getFileSystem(libUri);
                } catch (FileSystemNotFoundException e) {
                    fileSystem = FileSystems.newFileSystem(libUri, Collections.<String, Object>emptyMap());
                }
                libPath = fileSystem.getPath(sourceLib);
            } else {
                // this allows unit testing
                libPath = Paths.get(libUri);
            }
        } else {
            // try to locate as an external lib
            libPath = Paths.get(sourceLib);
        }

        logger.debug("Traversing [{}] for PGP signature verifier implementation and related dependencies", libPath);

        if (!Files.exists(libPath)) {
            throw new IllegalArgumentException(String.format("%s dir cannot be found", sourceLib));
        }

        final List<URL> urlList = new ArrayList<>();
        Files.walkFileTree(libPath, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString();
                if (fileName.endsWith(".jar")) {
                    boolean copyJar = true;
                    Path jarInTargetLib = Paths.get(targetLib.toString(), fileName);
                    if (Files.exists(jarInTargetLib)) {
                        logger.trace("{} already exists at {}", fileName, targetLib);
                        if (Files.isReadable(jarInTargetLib)) {
                            copyJar = false;
                        } else {
                            // quick and dirty - this is a temporary solution until we download agent from the Fleet package registry
                            logger.info("{} is not readable for the current user, removing and copying new one", jarInTargetLib);
                            Files.delete(jarInTargetLib);
                        }
                    }
                    if (copyJar) {
                        logger.debug("Copying {} to {}", fileName, targetLib);
                        Files.copy(file, jarInTargetLib);
                    }
                    urlList.add(jarInTargetLib.toUri().toURL());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                throw exc;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        if (urlList.isEmpty()) {
            throw new IllegalArgumentException(String.format("%s dir is empty", sourceLib));
        } else {
            logger.debug("{} directory contents: {}", sourceLib, urlList);
        }

        return new PgpSignatureVerifierLoader(urlList.toArray(new URL[0]), verifierClassName);
    }

    private PgpSignatureVerifierLoader(URL[] urls, String verifierImplementationName) {
        super(urls, PgpSignatureVerifier.class.getClassLoader());
        this.verifierImplementationName = verifierImplementationName;
    }

    PgpSignatureVerifier loadPgpSignatureVerifier() throws Exception {
        Class<?> verifierImplementationClass = loadClass(verifierImplementationName);
        // done lazily so that we create the logger only after proper initialization
        Logger logger = LogManager.getLogger(PgpSignatureVerifierLoader.class);
        logger.debug("{} class loaded by {}", verifierImplementationClass.getName(), verifierImplementationClass.getClassLoader());
        return (PgpSignatureVerifier) verifierImplementationClass.getDeclaredConstructor().newInstance();
    }
}
