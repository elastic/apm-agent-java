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
import java.util.Stack;

public class PgpSignatureVerifierLoader extends URLClassLoader {

    private final String verifierImplementationName;

    static PgpSignatureVerifierLoader getInstance(final String sourceLib, final Path targetLib) throws Exception {

        // done lazily so that we create the logger only after proper initialization
        Logger logger = LogManager.getLogger(PgpSignatureVerifierLoader.class);

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

        final List<String> classes = new ArrayList<>();
        final List<URL> urlList = new ArrayList<>();
        final Stack<String> packageTree = new Stack<>();
        Files.walkFileTree(libPath, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.endsWith("classes")) {
                    // we must add a trailing slash so that the URLClassLoader looks for class files within the classes dir
                    urlList.add(dir.toUri().toURL());
                } else if (!dir.endsWith(sourceLib)) {
                    String subPackageName = dir.getFileName().toString();
                    if (subPackageName.endsWith(System.getProperty("file.separator"))) {
                        subPackageName = subPackageName.substring(0, subPackageName.length() - 1);
                    }
                    packageTree.push(subPackageName);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString();
                int indexOfClassExt = fileName.indexOf(".class");
                if (indexOfClassExt > 0) {
                    StringBuilder fqcn = new StringBuilder();
                    for (String subPackage : packageTree) {
                        fqcn.append(subPackage).append(".");
                    }
                    fqcn.append(fileName, 0, indexOfClassExt);
                    classes.add(fqcn.toString());
                } else {
                    Path jarInLib = Paths.get(targetLib.toString(), fileName);
                    if (!Files.isReadable(jarInLib)) {
                        Files.copy(file, jarInLib);
                    }
                    urlList.add(jarInLib.toUri().toURL());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                throw exc;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (!dir.endsWith(sourceLib) && !dir.endsWith("classes")) {
                    packageTree.pop();
                }
                return FileVisitResult.CONTINUE;
            }
        });

        if (urlList.isEmpty()) {
            throw new IllegalArgumentException(String.format("%s dir is empty", sourceLib));
        } else {
            logger.debug("{} directory contents: {}", sourceLib, urlList);
        }

        String verifierImplementationName;
        if (classes.isEmpty()) {
            throw new IllegalArgumentException(String.format("%s dir must contain an implementation of " +
                "PgpSignatureVerifier within a \"classes\" directory.", sourceLib));
        } else if (classes.size() > 1) {
            throw new IllegalArgumentException(String.format("%s dir contains more than one class file. Only a " +
                "single class, the PgpSignatureVerifier implementation, should be in the provided dir.", sourceLib));
        } else {
            verifierImplementationName = classes.get(0);
            logger.debug("Found a single class in {} dir: {}", sourceLib, verifierImplementationName);
        }

        return new PgpSignatureVerifierLoader(urlList.toArray(new URL[0]), verifierImplementationName);
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
