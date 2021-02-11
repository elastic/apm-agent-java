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

import net.bytebuddy.agent.ByteBuddyAgent;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

public class GetAgentProperties {

    public static void main(String[] args) throws Exception {
        getAgentAndSystemPropertiesCurrentUser(args[0]).store(System.out, null);
    }

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
            throw new RuntimeException(RemoteAttacher.toString(process.getErrorStream()));
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
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Error during attachment using: " + accessor, exception);
        }
    }
}
