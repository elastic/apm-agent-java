/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

public interface JvmDiscoverer {

    Collection<JvmInfo> discoverJvms() throws Exception;

    boolean isAvailable();

    class Compound implements JvmDiscoverer {

        private final List<JvmDiscoverer> jvmDiscoverers;

        public Compound(List<JvmDiscoverer> jvmDiscoverers) {
            this.jvmDiscoverers = jvmDiscoverers;
        }

        @Override
        public Collection<JvmInfo> discoverJvms() throws Exception {
            for (JvmDiscoverer jvmDiscoverer : jvmDiscoverers) {
                if (jvmDiscoverer.isAvailable()) {
                    return jvmDiscoverer.discoverJvms();
                }
            }
            throw new IllegalStateException("No jvm discoverer is available");
        }

        @Override
        public boolean isAvailable() {
            for (JvmDiscoverer jvmDiscoverer : jvmDiscoverers) {
                if (jvmDiscoverer.isAvailable()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * HotSpot JVMs creates a file like this: {@code $TMPDIR/hsperfdata_$USER/<pid>}
     */
    class ForHotSpotVm implements JvmDiscoverer {

        private static final Logger logger = LogManager.getLogger(ForHotSpotVm.class);
        private final List<String> tempDirs;
        private final UserRegistry userRegistry;

        public ForHotSpotVm(List<String> tempDirs, UserRegistry userRegistry) {
            this.tempDirs = tempDirs;
            this.userRegistry = userRegistry;
        }

        public static ForHotSpotVm withDiscoveredTempDirs(UserRegistry userRegistry) {
            List<String> tempDirs = new ArrayList<>();
            if (Platform.isMac()) {
                // on MacOS, each user has their own temp dir
                try {
                    tempDirs.addAll(UserRegistry.getAllUsersMacOs().getAllTempDirs());
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            } else {
                // this only works if the java.io.tmpdir property is not overridden as the hsperfdata_ files are stored in the default tmpdir
                // but we as we control the startup script for the attacher.jar that's fine
                tempDirs.add(System.getProperty("java.io.tmpdir"));
            }
            return new ForHotSpotVm(tempDirs, userRegistry);
        }

        @Override
        public Collection<JvmInfo> discoverJvms() {
            List<JvmInfo> result = new ArrayList<>();
            List<File> hsPerfdataFolders = getHsPerfdataFolders();
            logger.debug("Looking in the following folders for hsperfdata_<user>/<pid> files: {}", hsPerfdataFolders);
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
                        try {
                            Properties properties = GetAgentProperties.getAgentAndSystemProperties(jvmPidFile.getName(), userRegistry.get(user));
                            result.add(JvmInfo.of(jvmPidFile.getName(), user, properties));
                        } catch (Exception e) {
                            logger.warn("Unable to get properties from {}", jvmPidFile.getName());
                            logger.warn(e.getMessage(), e);
                        }
                    }
                }
            }
            return result;
        }

        @Override
        public boolean isAvailable() {
            return !JvmInfo.isJ9() && !getHsPerfdataFolders().isEmpty();
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

    class UsingPs implements JvmDiscoverer {

        private static final Logger logger = LogManager.getLogger(UsingPs.class);
        private final UserRegistry userRegistry;

        UsingPs(UserRegistry userRegistry) {
            this.userRegistry = userRegistry;
        }

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
                    try {
                        jvms.add(JvmInfo.of(pid, user, GetAgentProperties.getAgentAndSystemProperties(pid, userRegistry.get(user))));
                    } catch (Exception e) {
                        logger.debug("Although the ps aux output contains 'java', the process {} does not seem to be a Java process.", pid);
                        logger.debug(line);
                        logger.debug(e.getMessage(), e);
                    }
                }
            }
            process.waitFor();
            return jvms;
        }

        @Override
        public boolean isAvailable() {
            try {
                return !Platform.isWindows()
                    // attachment under hotspot involves executing a kill -3
                    // this would terminate false positive matching processes (ps aux | grep java)
                    && JvmInfo.isJ9()
                    && new ProcessBuilder("ps", "aux").start().waitFor() == 0;
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                return false;
            }
        }
    }
}
