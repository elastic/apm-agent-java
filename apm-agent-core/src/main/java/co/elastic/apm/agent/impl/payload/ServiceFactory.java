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
package co.elastic.apm.agent.impl.payload;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.util.VersionUtils;

import javax.annotation.Nullable;

public class ServiceFactory {

    public Service createService(CoreConfiguration coreConfiguration, @Nullable String frameworkName, @Nullable String frameworkVersion) {
        Service service = new Service()
            .withName(coreConfiguration.getServiceName())
            .withVersion(coreConfiguration.getServiceVersion())
            .withEnvironment(coreConfiguration.getEnvironment())
            .withAgent(new Agent("java", getAgentVersion()))
            .withRuntime(new RuntimeInfo("Java", System.getProperty("java.version")))
            .withLanguage(new Language("Java", System.getProperty("java.version")))
            .withNode(new Node(coreConfiguration.getServiceNodeName()));
        if (frameworkName != null && frameworkVersion != null) {
            service.withFramework(new Framework(frameworkName, frameworkVersion));
        }
        return service;
    }

    private String getAgentVersion() {
        String version = VersionUtils.getVersionFromPomProperties(ServiceFactory.class, "co.elastic.apm", "elastic-apm-agent");
        if (version == null) {
            return "unknown";
        }
        return version;
    }
}
