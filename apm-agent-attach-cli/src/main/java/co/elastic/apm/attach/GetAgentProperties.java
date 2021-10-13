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

import co.elastic.apm.attach.UserRegistry.CommandOutput;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
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
        JvmAttachUtils.getAgentAndSystemProperties(args[0]).store(System.out, null);
    }

    /**
     * Attaches to the VM with the given pid and gets the agent and system properties.
     *
     * @param pid  The pid of the target VM, If it is current VM, this method will fork a VM for self-attachment.
     * @param user The user that runs the target VM. If this is not the current user, this method will fork a VM that runs under this user.
     * @return The agent and system properties of the target VM.
     * @throws Exception In case an error occurs while attaching to the target VM.
     */
    public static Properties getAgentAndSystemProperties(String pid, UserRegistry.User user) throws Exception {
        if (user.isCurrentUser() && !JvmInfo.CURRENT_PID.equals(pid)) {
            return JvmAttachUtils.getAgentAndSystemProperties(pid);
        } else {
            return getAgentAndSystemPropertiesSwitchUser(pid, user);
        }
    }

    static Properties getAgentAndSystemPropertiesSwitchUser(String pid, UserRegistry.User user) throws IOException {
        CommandOutput output = user.executeAsUserWithCurrentClassPath(GetAgentProperties.class, Arrays.asList(pid, user.getUsername()));
        if (output.getExitCode() == 0) {
            Properties properties = new Properties();
            properties.load(new StringReader(output.getOutput().toString()));
            return properties;
        } else {
            throw new RuntimeException(output.getOutput().toString(), output.exceptionThrown);
        }
    }

}
