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

import java.util.Properties;

/**
 * Allows convenient access to current JVM utilities provided by bytebuddy which is relocated on packaging.
 */
public class JvmAttachUtils {

    public static final String CURRENT_PID = ByteBuddyAgent.ProcessProvider.ForCurrentVm.INSTANCE.resolve();

    public static Properties getAgentAndSystemProperties(String pid) {
        ByteBuddyAgent.AttachmentProvider.Accessor accessor = ElasticAttachmentProvider.get().attempt();
        if (!accessor.isAvailable()) {
            throw new IllegalStateException("No compatible attachment provider is available");
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
        } catch (Exception e) {
            throw new IllegalStateException("Error during attachment using: " + accessor, e);
        }
    }

}
