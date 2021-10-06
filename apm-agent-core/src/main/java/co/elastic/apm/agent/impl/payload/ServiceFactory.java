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
package co.elastic.apm.agent.impl.payload;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.util.VersionUtils;

public class ServiceFactory {

    public Service createService(CoreConfiguration coreConfiguration, String ephemeralId, boolean runsOnLambda) {
        Service service = new Service();
        if (runsOnLambda) {
            augmentServiceForAWSLambda(service, coreConfiguration, ephemeralId);
        } else {
            service = new Service()
                .withName(coreConfiguration.getServiceName())
                .withVersion(coreConfiguration.getServiceVersion())
                .withNode(new Node(coreConfiguration.getServiceNodeName()))
                .withRuntime(new RuntimeInfo("Java", System.getProperty("java.version")));
        }

        return service.withEnvironment(coreConfiguration.getEnvironment())
            .withAgent(new Agent("java", getAgentVersion(), ephemeralId))
            .withLanguage(new Language("Java", System.getProperty("java.version")));
    }

    private void augmentServiceForAWSLambda(Service service, CoreConfiguration coreConfiguration, String ephemeralId) {
        String serviceName = System.getenv("AWS_LAMBDA_FUNCTION_NAME");
        if (null != serviceName) {
            service.withName(serviceName);
        }

        String serviceVersion = System.getenv("AWS_LAMBDA_FUNCTION_VERSION");
        if (null != serviceVersion) {
            service.withVersion(serviceVersion);
        }

        String runtimeName = System.getenv("AWS_EXECUTION_ENV");
        runtimeName = null != runtimeName ? runtimeName : "AWS_Lambda_java";
        service.withRuntime(new RuntimeInfo(runtimeName, System.getProperty("java.version")));

        String serviceNodeName = System.getenv("AWS_LAMBDA_LOG_STREAM_NAME");
        if (null != serviceNodeName) {
            service.withNode(new Node(serviceNodeName));
        }
    }

    private String getAgentVersion() {
        String version = VersionUtils.getAgentVersion();
        if (version == null) {
            return "unknown";
        }
        return version;
    }
}
