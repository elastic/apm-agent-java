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
package co.elastic.apm.attach;

import com.sun.jna.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public interface JvmDiscoverer {

    Collection<JvmInfo> discoverJvms() throws Exception;

    boolean isAvailable();

    enum ForCurrentVM implements JvmDiscoverer {
        INSTANCE;

        private final JvmDiscoverer delegate;

        ForCurrentVM() {
            JvmDiscoverer tempJvmDiscoverer = Unavailable.INSTANCE;
            for (JvmDiscoverer jvmDiscoverer : Arrays.asList(UsingPs.INSTANCE, ForHotSpotVm.withDefaultTempDir())) {
                if (jvmDiscoverer.isAvailable()) {
                    tempJvmDiscoverer = jvmDiscoverer;
                    break;
                } else {
                    System.out.println(jvmDiscoverer.getClass().getSimpleName() + " is not available");
                }
            }
            delegate = tempJvmDiscoverer;
        }

        @Override
        public Collection<JvmInfo> discoverJvms() throws Exception {
            return delegate.discoverJvms();
        }

        @Override
        public boolean isAvailable() {
            return delegate.isAvailable();
        }
    }

    class JpsFinder {
        // package protected for testing
        static List<Path> getJpsPaths(Properties systemProperties, Map<String, String> env) {

            List<Path> list = new ArrayList<Path>();

            String os = systemProperties.getProperty("os.name");
            Path binaryName;
            if (os != null && os.startsWith("Windows")) {
                binaryName = Paths.get("jps.exe");
            } else {
                binaryName = Paths.get("jps");
            }


            for (String javaHome : Arrays.asList(env.get("JAVA_HOME"), systemProperties.getProperty("java.home"))) {
                if (javaHome != null) {
                    list.add(Paths.get(javaHome)
                        .resolve("bin")
                        .resolve(binaryName));

                    // in case 'java.home' or JAVA_HOME are set to a JRE
                    // we try to use the one in the folder up, which is usually where the JDK is
                    list.add(Paths.get(javaHome)
                        .resolve("..")
                        .resolve("bin")
                        .resolve(binaryName));

                }
            }

            // fallback to the simple binary name
            list.add(binaryName);

            return list;
        }

        static Path getJpsPath(Properties systemProperties, Map<String, String> env) {
            List<Path> locations = getJpsPaths(systemProperties, env);
            for (Path path : locations) {
                if (Files.isExecutable(path)) {
                    return path;
                }
            }
            throw new IllegalStateException("unable to locate jps executable, searched locations : " + locations);
        }

        static Path getJpsPath() {
            return getJpsPath(System.getProperties(), System.getenv());
        }
    }

    enum Unavailable implements JvmDiscoverer {
        INSTANCE;

        @Override
        public Collection<JvmInfo> discoverJvms() {
            throw new IllegalStateException("Can't discover JVMs for this platform");
        }

        @Override
        public boolean isAvailable() {
            return false;
        }
    }

    /**
     * HotSpot JVMs creates a file like this: {@code $TMPDIR/hsperfdata_$USER/<pid>}
     */
    class ForHotSpotVm implements JvmDiscoverer {

        private static Logger logger = LogManager.getLogger(JvmDiscoverer.class);

        private final List<String> tempDirs;

        public ForHotSpotVm(List<String> tempDirs) {
            this.tempDirs = tempDirs;
        }

        public static ForHotSpotVm withDefaultTempDir() {
            List<String> tempDirs = new ArrayList<>();
            if (Platform.isMac()) {
                // on MacOS, each user has their own temp dir
                try {
                    tempDirs.addAll(Users.getAllUsersMacOs().getAllTempDirs());
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            } else {
                // this only works if the java.io.tmpdir property is not overridden as the hsperfdata_ files are stored in the default tmpdir
                // but we as we control the startup script for the attacher.jar that's fine
                tempDirs.add(System.getProperty("java.io.tmpdir"));
            }
            return new ForHotSpotVm(tempDirs);
        }

        @Override
        public Collection<JvmInfo> discoverJvms() {
            List<JvmInfo> result = new ArrayList<>();
            List<File> hsPerfdataFolders = getHsPerfdataFolders();
            for (File hsPerfdataFolder : hsPerfdataFolders) {
                File[] jvmPidFiles = hsPerfdataFolder.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        return file.isFile() && file.canRead() && file.getName().matches("\\d+");
                    }
                });
                if (jvmPidFiles != null) {
                    for (File jvmPidFile : jvmPidFiles) {
                        String user = jvmPidFile.getParentFile().getName().substring("hsperfdata_".length());
                        // TODO parse hsperfdata_ file to get jar name and vm arguments
                        result.add(new JvmInfo(jvmPidFile.getName(), user));
                    }
                }
            }
            return result;
        }

        @Override
        public boolean isAvailable() {
            List<File> files = getHsPerfdataFolders();
            return !files.isEmpty();
        }

        private List<File> getHsPerfdataFolders() {
            List<File> result = new ArrayList<>();
            for (String tempDir : tempDirs) {
                File[] files = new File(tempDir).listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.startsWith("hsperfdata_");
                    }
                });
                if (files != null) {
                    result.addAll(Arrays.asList(files));
                }
            }
            return result;
        }
    }

    enum UsingPs implements JvmDiscoverer {

        INSTANCE;

        private static final Logger logger = LogManager.getLogger(UsingPs.class);

        @Override
        public Collection<JvmInfo> discoverJvms() throws Exception {
            Collection<JvmInfo> jvms = new ArrayList<>();
            Process process = new ProcessBuilder("ps", "aux").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.contains("java")) {
                    String[] rows = line.split("\\s+");
                    String pid = rows[1];
                    String user = rows[0];
                    jvms.add(new JvmInfo(pid, user));
                }
            }
            process.waitFor();
            return jvms;
        }

        @Override
        public boolean isAvailable() {
            try {
                return !Platform.isWindows() && new ProcessBuilder("ps", "aux")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor() == 0;
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return false;
            }
        }
    }
}
