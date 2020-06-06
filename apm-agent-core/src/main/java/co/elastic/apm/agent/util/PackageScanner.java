/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
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
     * @return all class names within a package and sub-packages
     * @throws IOException
     * @throws URISyntaxException
     */
    public static List<String> getClassNames(final String basePackage) throws IOException, URISyntaxException {
        String baseFolderResource = basePackage.replace('.', '/');
        final List<String> classNames = new ArrayList<>();
        Enumeration<URL> resources = getResourcesFromAgentClassLoader(baseFolderResource);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            URI uri = resource.toURI();
            if (uri.getScheme().equals("jar")) {
                // avoids FileSystemAlreadyExistsException
                synchronized (PackageScanner.class) {
                    try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap())) {
                        final Path basePath  = fileSystem.getPath(baseFolderResource).toAbsolutePath();
                        classNames.addAll(listClassNames(basePackage, basePath));
                    }
                }
            } else {
                final Path basePath = Paths.get(uri);
                if (basePath.toString().contains("test-classes")) {
                    continue;
                }
                classNames.addAll(listClassNames(basePackage, basePath));
            }
        }
        return classNames;
    }

    private static List<String> listClassNames(final String basePackage, final Path basePath) throws IOException {
        final List<String> classNames = new ArrayList<>();
        Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".class")) {
                    classNames.add(basePackage + "." + basePath.relativize(file).toString().replace('/', '.').replace(".class", ""));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return classNames;
    }

    private static Enumeration<URL> getResourcesFromAgentClassLoader(String baseFolderResource) throws IOException {
        ClassLoader agentCL = PackageScanner.class.getClassLoader();
        if (agentCL == null) {
            agentCL = ClassLoader.getSystemClassLoader();
        }
        return agentCL.getResources(baseFolderResource);
    }
}
