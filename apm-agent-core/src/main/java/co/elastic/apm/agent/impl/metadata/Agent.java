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
package co.elastic.apm.agent.impl.metadata;


import co.elastic.apm.agent.configuration.ActivationMethod;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.util.PrivilegedActionUtils;

import javax.annotation.Nullable;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.UUID;

/**
 * Name and version of the Elastic APM agent
 */
public class Agent {

    /**
     * Name of the Elastic APM agent, e.g. "Python"
     * (Required)
     */
    private final String name;
    /**
     * Version of the Elastic APM agent, e.g."1.0.0"
     * (Required)
     */
    private final String version;

    /**
     * A unique agent ID, non-persistent (i.e. changes on restart).
     * <a href="https://www.elastic.co/guide/en/ecs/master/ecs-agent.html#_agent_field_details">See ECS for reference</a>.
     */
    private final String ephemeralId;

    /**
     * The way the agent was activated, e.g."javaagent-flag"
     * per <a href="https://github.com/elastic/apm/blob/main/specs/agents/metadata.md#activation-method">the activation method spec</a>
     * (Required)
     */
    private final String activationMethod;

    public Agent(String name, String version) {
        this(name, version, UUID.randomUUID().toString(), null);
    }

    public Agent(String name, String version, String ephemeralId, @Nullable CoreConfiguration coreConfiguration) {
        this.name = name;
        this.version = version;
        this.ephemeralId = ephemeralId;
        this.activationMethod = getActivationMethod(coreConfiguration);
    }

    /**
     * Name of the Elastic APM agent, e.g. "Python"
     * (Required)
     */
    public String getName() {
        return name;
    }

    /**
     * Version of the Elastic APM agent, e.g."1.0.0"
     * (Required)
     */
    public String getVersion() {
        return version;
    }

    /**
     * @return A unique agent ID, non-persistent (i.e. changes on restart).
     */
    public String getEphemeralId() {
        return ephemeralId;
    }

    /**
     * Activation method of the Elastic APM agent, e.g."javaagent-flag"
     * (Required)
     */
    public String getActivationMethod() {
        return activationMethod;
    }

    private static String getActivationMethod(@Nullable CoreConfiguration coreConfiguration) {
        ActivationMethod activation = ActivationMethod.UNKNOWN;
        if (coreConfiguration != null) {
            activation = coreConfiguration.getActivationMethod();
            if (activation.equals(ActivationMethod.UNKNOWN)) {
                //Need to infer it
                String elasticJavaagentOnTheCommandline = getElasticJavaagentOnTheCommandline();
                String javaToolOptions = PrivilegedActionUtils.getEnv("JAVA_TOOL_OPTIONS");
                if (javaToolOptions != null && elasticJavaagentOnTheCommandline != null && javaToolOptions.contains(elasticJavaagentOnTheCommandline)) {
                        activation = ActivationMethod.ENV_ATTACH;
                } else if(elasticJavaagentOnTheCommandline != null) {
                    activation = ActivationMethod.JAVAAGENT_FLAG;
                }
            }
        }
        return activation.toReferenceString();
    }

    @Nullable
    private static String getAgentJarFilename() {
        String agentLocation = PrivilegedActionUtils.getProtectionDomain(GlobalTracer.class).getCodeSource().getLocation().getFile();
        if (agentLocation != null) {
            String agentJarFile = agentLocation.replace('\\', '/');
            if (agentJarFile.contains("/")) {
                return agentJarFile.substring(agentLocation.lastIndexOf('/') + 1, agentLocation.length());
            } else {
                return agentJarFile;
            }
        } else {
            return null;
        }
    }

    @Nullable
    private static String getElasticJavaagentOnTheCommandline() {
        String agentJarFile = getAgentJarFilename();
        if (agentJarFile != null) {
            List<String> javaArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
            if (javaArgs != null) {
                //if there is more than one, this will return the first, which is the correct algorithm
                for (String javaArg : javaArgs) {
                    if (javaArg.startsWith("-javaagent:") && javaArg.contains(agentJarFile)) {
                        return javaArg;
                    }
                }
            }
        }
        return null;
    }
}
