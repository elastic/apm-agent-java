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
package co.elastic.apm.agent.configuration;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.util.PrivilegedActionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.management.ManagementFactory;
import java.util.List;

public enum ActivationType {
    K8S_ATTACH, // https://github.com/elastic/apm-mutating-webhook
    PROGRAMMATIC_SELF_ATTACH, // ElasticApmAttacher.attach();
    JAVAAGENT_FLAG, // -javaagent:... command-line option used
    AWS_LAMBDA_LAYER, // Only if installed by using layers (as other metadata already identifies lambda)
    ENV_ATTACH, // JAVA_TOOL_OPTIONS env var used to specify -javaagent option
    APM_AGENT_ATTACH_CLI, // 'java -jar apm-agent-attach-cli.jar ...' used
    FLEET, // Fleet using 'java -jar apm-agent-attach-cli.jar ...' to attach (directly or through webhook)

    AZURE_FUNCTIONS, //Not defined yet how this is identified
    UNKNOWN;

    private static final Logger logger = LogManager.getLogger(ActivationType.class);

    public String toReferenceString() {
        return toString().replace('_', '-').toLowerCase();
    }

    public static ActivationType findType() {
        ActivationType type = _findType();
        logger.debug("findType() returning {}",type);
        return type;
    }

    public static ActivationType _findType() {
        // Really just a big `switch` type method

        ElasticApmTracer tracer = GlobalTracer.getTracerImpl();
        if (tracer == null) {
            //Not yet finished initializing, or we're in test mode, in which case
            return UNKNOWN;
        }

        CoreConfiguration.ActivationMethod method = tracer.getConfig(CoreConfiguration.class).getActivationMethod();
        logger.debug("CoreConfiguration.ActivationMethod: {}", method);
        if (!method.equals(CoreConfiguration.ActivationMethod.NONE)){
            //the activation method was specified by the attacher, so use this as best option
            switch (method) {
                case K8S: return K8S_ATTACH;
                case REMOTE: return APM_AGENT_ATTACH_CLI;
                case SELF_ATTACH: return PROGRAMMATIC_SELF_ATTACH;
                case FLEET: return FLEET;
            }
        }

        //The next picks up `Container image' lambdas but only if the layer is used, which we
        //aren't sure is good or bad. We could also check ELASTIC_APM_AWS_LAMBDA_HANDLER
        //but have deliberately decided not to (for now)
        String lambdaWrapper = PrivilegedActionUtils.getEnv("AWS_LAMBDA_EXEC_WRAPPER");
        logger.debug("AWS_LAMBDA_EXEC_WRAPPER: {}", lambdaWrapper);
        if (null != lambdaWrapper && lambdaWrapper.equals("/opt/elastic-apm-handler")) {
            return AWS_LAMBDA_LAYER;
        }

        String elasticJavaagentOnTheCommandline = getElasticJavaagentOnTheCommandline();
        logger.debug("Agent on command line: {}", elasticJavaagentOnTheCommandline);
        String javaToolOptions = PrivilegedActionUtils.getEnv("JAVA_TOOL_OPTIONS");
        logger.debug("JAVA_TOOL_OPTIONS: {}", javaToolOptions);
        if (null != javaToolOptions) {
            // fallback k8s test, this is the pattern used by the webhook, but it's
            // a fragile check as the webhook could easily be changed
            if (javaToolOptions.contains("-javaagent:/elastic/apm/agent/elastic-apm-agent.jar")) {
                return K8S_ATTACH;
            } else if(elasticJavaagentOnTheCommandline != null && javaToolOptions.contains(elasticJavaagentOnTheCommandline)) {
                return ENV_ATTACH;
            }
        }

        if(elasticJavaagentOnTheCommandline != null) {
            return JAVAAGENT_FLAG;
        }

        return UNKNOWN;
    }

    public static String getAgentJarFilename() {
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

    public static String getElasticJavaagentOnTheCommandline() {
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
