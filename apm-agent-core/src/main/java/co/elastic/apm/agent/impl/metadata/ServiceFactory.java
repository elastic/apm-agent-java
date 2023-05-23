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

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.ServerlessConfiguration;
import co.elastic.apm.agent.util.PrivilegedActionUtils;
import co.elastic.apm.agent.util.VersionUtils;

public class ServiceFactory {

    public Service createService(CoreConfiguration coreConfiguration, String ephemeralId, ServerlessConfiguration serverlessConfiguration) {
        Service service = new Service()
            .withName(coreConfiguration.getServiceName())
            .withVersion(coreConfiguration.getServiceVersion())
            .withEnvironment(coreConfiguration.getEnvironment())
            .withAgent(new Agent("java", VersionUtils.getAgentVersion(), ephemeralId, coreConfiguration))
            .withRuntime(new RuntimeInfo("Java", System.getProperty("java.version")))
            .withLanguage(new Language("Java", System.getProperty("java.version")))
            .withNode(new Node(coreConfiguration.getServiceNodeName()));

        if (serverlessConfiguration.runsOnAwsLambda()) {
            augmentServiceForAWSLambda(service);
        }
        return service;
    }

    private void augmentServiceForAWSLambda(Service service) {
        String runtimeName = PrivilegedActionUtils.getEnv("AWS_EXECUTION_ENV");
        runtimeName = null != runtimeName ? runtimeName : "AWS_Lambda_java";
        service.withRuntime(new RuntimeInfo(runtimeName, System.getProperty("java.version")));

        Node node = service.getNode();
        String nodeName = (node != null) ? node.getName() : null;
        if (nodeName == null || nodeName.isEmpty()) {
            String serviceNodeName = PrivilegedActionUtils.getEnv("AWS_LAMBDA_LOG_STREAM_NAME");
            if (null != serviceNodeName) {
                service.withNode(new Node(serviceNodeName));
            }
        }
    }
}
