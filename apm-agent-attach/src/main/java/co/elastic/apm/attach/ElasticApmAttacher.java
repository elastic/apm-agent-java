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
package co.elastic.apm.attach;

import net.bytebuddy.agent.ByteBuddyAgent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Attaches the Elastic Apm agent to the current or a remote JVM
 */
public class ElasticApmAttacher {

    /**
     * Attaches the Elastic Apm agent to the current JVM.
     * <p>
     * This method may only be invoked once.
     * </p>
     *
     * @throws IllegalStateException if there was a problem while attaching the agent to this VM
     */
    public static void attach() {
        ByteBuddyAgent.attach(AgentJarFileHolder.INSTANCE.agentJarFile, ByteBuddyAgent.ProcessProvider.ForCurrentVm.INSTANCE);
    }

    /**
     * Attaches the agent to a remote JVM
     *
     * @param pid       the PID of the JVM the agent should be attached on
     * @param agentArgs the agent arguments
     */
    public static void attach(String pid, String agentArgs) {
        ByteBuddyAgent.attach(AgentJarFileHolder.INSTANCE.agentJarFile, pid, agentArgs);
    }

    private enum AgentJarFileHolder {
        INSTANCE;

        // initializes lazily and ensures it's only loaded once
        final File agentJarFile = getAgentJarFile();

        private static File getAgentJarFile() {
            try (InputStream agentJar = ElasticApmAttacher.class.getResourceAsStream("/elastic-apm-agent.jar")) {
                if (agentJar == null) {
                    throw new IllegalStateException("Agent jar not found");
                }
                // don't delete on exit, because this the attaching application may terminate before the target application
                File tempAgentJar = File.createTempFile("elastic-apm-agent", ".jar");
                try (OutputStream out = new FileOutputStream(tempAgentJar)) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = agentJar.read(buffer)) != -1) {
                        out.write(buffer, 0, length);
                    }
                }
                return tempAgentJar;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
