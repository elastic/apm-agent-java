/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public interface JvmDiscoverer {

    Collection<JvmInfo> discoverJvms() throws Exception;

    boolean isAvailable();

    enum ForCurrentVM implements JvmDiscoverer {
        INSTANCE;

        private final JvmDiscoverer delegate;

        ForCurrentVM() {
            JvmDiscoverer tempJvmDiscoverer = Unavailable.INSTANCE;
            for (JvmDiscoverer jvmDiscoverer : Arrays.asList(Jps.INSTANCE, ForHotSpotVm.withDefaultTempDir())) {
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

    enum Jps implements JvmDiscoverer {
        INSTANCE;

        @Nonnull
        private static String getJpsOutput() throws IOException, InterruptedException {
            final Process jps = new ProcessBuilder("jps", "-lv").start();
            if (jps.waitFor() == 0) {
                return RemoteAttacher.toString(jps.getInputStream());
            } else {
                throw new IllegalStateException(RemoteAttacher.toString(jps.getErrorStream()));
            }
        }

        @Override
        public Collection<JvmInfo> discoverJvms() throws Exception {
            return getJVMs(getJpsOutput());
        }

        @Nonnull
        private Set<JvmInfo> getJVMs(String jpsOutput) {
            Set<JvmInfo> set = new HashSet<>();
            for (String s : jpsOutput.split("\n")) {
                JvmInfo parse = JvmInfo.parse(s);
                set.add(parse);
            }
            return set;
        }

        @Override
        public boolean isAvailable() {
            try {
                return new ProcessBuilder("jps", "-lv").start().waitFor() == 0;
            } catch (Exception e) {
                return false;
            }
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

        private final String tempDir;

        public ForHotSpotVm(String tempDir) {
            this.tempDir = tempDir;
        }

        public static ForHotSpotVm withDefaultTempDir() {
            String temporaryDirectory;
            if (Platform.isMac()) {
                temporaryDirectory = System.getenv("TMPDIR");
                if (temporaryDirectory == null) {
                    temporaryDirectory = "/tmp";
                }
            } else if (Platform.isWindows()) {
				temporaryDirectory = System.getenv("TEMP");
				if (temporaryDirectory == null) {
                    temporaryDirectory = "c:/Temp";
                }
			} else {
                temporaryDirectory = "/tmp";
            }
            return new ForHotSpotVm(temporaryDirectory);
        }

        @Override
        public Collection<JvmInfo> discoverJvms() {
            List<JvmInfo> result = new ArrayList<>();
            File[] hsPerfdataFolders = getHsPerfdataFolders();
            if (hsPerfdataFolders != null) {
                for (File hsPerfdataFolder : hsPerfdataFolders) {
                    File[] jvmPidFiles = hsPerfdataFolder.listFiles(new FileFilter() {
                        @Override
                        public boolean accept(File file) {
                            return file.isFile() && file.canRead() && file.getName().matches("\\d+");
                        }
                    });
                    if (jvmPidFiles != null) {
                        for (File jvmPidFile : jvmPidFiles) {
                            result.add(new JvmInfo(jvmPidFile.getName(), null));
                        }
                    }
                }
            }
            return result;
        }

        @Override
        public boolean isAvailable() {
            File[] files = getHsPerfdataFolders();
            return files != null && files.length > 0;
        }

        private File[] getHsPerfdataFolders() {
            return new File(tempDir).listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith("hsperfdata_");
                }
            });
        }
    }
}
