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
package co.elastic.apm.agent.common.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AgentInfo {

    private static final Set<String> dependencyPackages = new HashSet<>(Arrays.asList(
        "org.slf4j",
        "org.apache.logging",
        "net.bytebuddy",
        "org.objectweb.asm",
        "org.jctools" ,
        "org.stagemonitor",
        "org.HdrHistogram",
        "co.elastic.logging",
        "com.blogspot.mydailyjava.weaklockfree",
        "com.lmax.disruptor",
        "com.dslplatform.json",
        "com.googlecode.concurrentlinkedhashmap"
    ));

    private static final Set<String> agentRootPackages = new HashSet<>(Arrays.asList(
        "co.elastic.apm",
        "bootstrap.co.elastic.apm",
        "bootstrap.java.lang"
    ));

    /**
     * Returns a list of packages of dependencies used by the agent.
     * Should be updated manually whenever a new dependency is added to the agent code.
     * See {@code co.elastic.apm.agent.premain.AgentPackagingIT#validateDependencyPackages()}.
     * @return a list of root packages of dependencies used by the agent.
     */
    public static Set<String> getAgentDependencyPackages() {
        return dependencyPackages;
    }

    /**
     * Returns a list of root packages of agent classes.
     * Should be updated manually whenever a new root is added to the agent code.
     * See {@code co.elastic.apm.agent.premain.AgentPackagingIT#validateDependencyPackages()}.
     * @return a list of agent root packages.
     */
    public static Set<String> getAgentRootPackages() {
        return agentRootPackages;
    }
}
