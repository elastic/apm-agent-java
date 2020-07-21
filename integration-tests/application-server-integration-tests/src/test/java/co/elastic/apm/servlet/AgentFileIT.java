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
package co.elastic.apm.servlet;

import co.elastic.apm.agent.bci.AgentMain;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentFileIT {

    static String getPathToJavaagent() {
        return getTargetJar("elastic-apm-agent", "");
    }

    static String getPathToAttacher() {
        return getTargetJar("apm-agent-attach", "-standalone");
    }

    @Nullable
    private static String getTargetJar(String project, String classifier) {
        File agentBuildDir = new File("../../" + project + "/target/");
        FileFilter fileFilter = file -> file.getName().matches(project + "-\\d\\.\\d+\\.\\d+(-SNAPSHOT)?" + classifier + ".jar");
        return Arrays.stream(agentBuildDir.listFiles(fileFilter)).findFirst()
            .map(File::getAbsolutePath)
            .orElse(null);
    }

    @Test
    public void testEverythingIsShaded() throws IOException {
        final String pathToJavaagent = getPathToJavaagent();
        assertThat(pathToJavaagent).isNotNull();

        // while shading is tested at runtime during agent startup, it does not cost much to check this twice
        // in case agent isn't started anywhere or container/server integration tests are skipped, this ensures
        // that we get a proper failed test when this happens.
        AgentMain.verifyProperAgentShading(new File(pathToJavaagent));
    }
}
