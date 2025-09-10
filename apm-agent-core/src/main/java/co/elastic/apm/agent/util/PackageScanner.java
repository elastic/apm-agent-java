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
package co.elastic.apm.agent.util;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class PackageScanner {

    /**
     * Returns all class names within a package and sub-packages
     *
     * @param basePackage the package to scan
     * @param classLoader
     * @return all class names within a package and sub-packages
     * @throws IOException
     * @throws URISyntaxException
     */
    public static List<String> getClassNames(final String basePackage, ClassLoader classLoader) throws IOException, URISyntaxException {
        // This method is called from indy instrumentation call site method resolution, which happens within the application
        // threads. Those threads being owned by the application, they could have been interrupted and have their
        // 'interrupted' status still set, which in turn makes the IO read throw 'ClosedByInterruptException'.
        //
        // When this happens, we can clear the 'interrupted' status, retry once and then restore the 'interrupted'
        // state as it was before this method was called.

        List<String> list;
        try {
            list = doGetClassNames(basePackage, classLoader);
        } catch (ClosedByInterruptException e) {
            // clears the 'interrupted' status, expected to return true as exception was thrown
            boolean interrupted = Thread.interrupted();

            try {
                list = doGetClassNames(basePackage, classLoader);
            } finally {
                if (interrupted) {
                    // restore the 'interrupted' status
                    Thread.currentThread().interrupt();
                }
            }

        }

        return list;
    }

    private static List<String> doGetClassNames(String basePackage, ClassLoader classLoader) throws IOException, URISyntaxException {
        String baseFolderResource = basePackage.replace('.', '/');
        final List<String> classNames = new ArrayList<>();
        Enumeration<URL> resources = classLoader.getResources(baseFolderResource);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            URI uri = resource.toURI();
            List<String> result;
            if (uri.getScheme().equals("jar")) {
                synchronized (PackageScanner.class) {
                    try (FileSystem fileSystem = getFileSystem(uri)) {
                        Path basePath  = fileSystem.getPath(baseFolderResource).toAbsolutePath();
                        if (!Files.exists(basePath)) { // called in a privileged action, thus no need to deal with security manager
                            basePath = fileSystem.getPath("agent/" + baseFolderResource).toAbsolutePath();
                        }
                        result = listClassNames(basePackage, basePath);
                    }
                }
            } else {
                result = listClassNames(basePackage, Paths.get(uri));
            }
            classNames.addAll(result);
        }
        return classNames;
    }

    @Nullable
    private static FileSystem getFileSystem(URI uri) throws IOException {
        FileSystem fileSystem;
        // FileSystemAlreadyExistsException is thrown when FS has already been opened
        // FileSystemNotFoundException is thrown when FS has not already been opened
        // thus we can't avoid throwing exceptions, but we can use them for transparent fallback
        // multiple calls for equivalent URIs is expected with "fat jar" that have nested paths with "!/../path/within/jar"
        try {
            fileSystem = FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap());
        } catch (FileSystemAlreadyExistsException e) {
            fileSystem = FileSystems.getFileSystem(uri);
        }
        return fileSystem;
    }

    /**
     * Lists all classes in the provided path, as part of the provided base package
     * @param basePackage the package to prepend to all class files found in the relative path
     * @param basePath the base lookup path. <b>NOTE: this is a file system path, as opposed to the Java class resource
     *                 path, localized to the file system. Specifically, we need to use the proper {@link File#separatorChar}</b>.
     * @return a list of fully qualified class names from the scanned package
     * @throws IOException error in file tree scanning
     */
    private static List<String> listClassNames(final String basePackage, final Path basePath) throws IOException {
        final List<String> classNames = new ArrayList<>();
        Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".class") || file.toString().endsWith(".esclazz")) {
                    // We need to escape both the filesystem-specific separator and the explicit `/` separator that may be added by the relativize() implementation
                    String classNameSuffix = basePath.relativize(file).toString()
                        .replace(System.getProperty("file.separator"), ".")
                        .replace('/', '.')
                        .replace(".class", "")
                        .replace(".esclazz", "");
                    classNames.add(basePackage + "." + classNameSuffix);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return classNames;
    }

}
