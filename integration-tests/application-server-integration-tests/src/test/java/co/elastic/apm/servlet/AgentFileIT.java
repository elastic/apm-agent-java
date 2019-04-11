/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.servlet;

import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentFileIT {

    static String getPathToJavaagent() {
        File agentBuildDir = new File("../../elastic-apm-agent/target/");
        FileFilter fileFilter = new WildcardFileFilter("elastic-apm-agent-*.jar");
        for (File file : agentBuildDir.listFiles(fileFilter)) {
            if (!file.getAbsolutePath().endsWith("javadoc.jar") && !file.getAbsolutePath().endsWith("sources.jar")) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    @Test
    void testEverythingIsShaded() throws IOException {
        final String pathToJavaagent = getPathToJavaagent();
        assertThat(pathToJavaagent).isNotNull();
        try (JarFile agentJar = new JarFile(new File(pathToJavaagent))) {
            assertThat(
                agentJar.stream()
                    .map(JarEntry::getName)
                    .filter(entry -> !entry.startsWith("META-INF/"))
                    .filter(entry -> !entry.startsWith("co/"))
                    .filter(entry -> !entry.startsWith("schema/")))
                .isEmpty();
        }
    }
}
