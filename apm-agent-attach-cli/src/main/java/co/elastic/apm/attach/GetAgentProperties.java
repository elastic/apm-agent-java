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

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.VirtualMachine;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

public class GetAgentProperties {

    /**
     * Prints the system and agent properties of the JVM with the provided pid to stdout,
     * it a way that can be consumed by {@link Properties#load(InputStream)}.
     * This works by attaching to the JVM with the provided pid and by calling
     * {@link com.sun.tools.attach.VirtualMachine#getSystemProperties()} and {@link com.sun.tools.attach.VirtualMachine#getAgentProperties()}.
     * <p>
     * In {@link #getAgentAndSystemPropertiesSwitchUser}, a new JVM is forked running this main method.
     * This JVM runs in the context of the same user that runs the JVM with the provided pid.
     * This indirection is needed as it's not possible to attach to a JVM that runs under a different user.
     * </p>
     *
     * @param args contains a single argument - the process id of the target VM
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        getAgentAndSystemPropertiesCurrentUser(args[0]).store(System.out, null);
    }

    /**
     * Attaches to the VM with the given pid and gets the {@link VirtualMachine#getAgentProperties()} and {@link VirtualMachine#getSystemProperties()}.
     *
     * @param pid  The pid of the target VM.
     * @param user The user that runs the target VM. If this is not the current user, this method will fork a VM that runs under this user.
     * @return The agent and system properties of the target VM.
     * @throws Exception In case an error occurs while attaching to the target VM.
     */
    public static Properties getAgentAndSystemProperties(String pid, UserRegistry.User user) throws Exception {
        if (user.isCurrentUser()) {
            return getAgentAndSystemPropertiesCurrentUser(pid);
        } else {
            return getAgentAndSystemPropertiesSwitchUser(pid, user);
        }
    }

    static Properties getAgentAndSystemPropertiesSwitchUser(String pid, UserRegistry.User user) throws IOException, InterruptedException {
        Process process = user.runAsUserWithCurrentClassPath(GetAgentProperties.class, Arrays.asList(pid, user.getUsername())).start();
        if (process.waitFor() == 0) {
            Properties properties = new Properties();
            properties.load(process.getInputStream());
            return properties;
        } else {
            throw new RuntimeException(AgentAttacher.toString(process.getErrorStream()));
        }
    }

    static Properties getAgentAndSystemPropertiesCurrentUser(String pid) {
        ByteBuddyAgent.AttachmentProvider.Accessor accessor = ElasticAttachmentProvider.get().attempt();
        if (!accessor.isAvailable()) {
            throw new IllegalStateException("No compatible attachment provider is available");
        }
        if (accessor.isExternalAttachmentRequired() && pid.equals(JvmInfo.CURRENT_PID)) {
            throw new IllegalStateException("The current accessor does not allow to attach to the current VM");
        }

        try {
            Class<?> vm = accessor.getVirtualMachineType();
            Object virtualMachineInstance = vm
                .getMethod("attach", String.class)
                .invoke(null, pid);
            try {
                Properties agentProperties = (Properties) vm.getMethod("getAgentProperties").invoke(virtualMachineInstance);
                Properties systemProperties = (Properties) vm.getMethod("getSystemProperties").invoke(virtualMachineInstance);
                systemProperties.putAll(agentProperties);
                return systemProperties;
            } finally {
                vm.getMethod("detach").invoke(virtualMachineInstance);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Error during attachment using: " + accessor, exception);
        }
    }
}
