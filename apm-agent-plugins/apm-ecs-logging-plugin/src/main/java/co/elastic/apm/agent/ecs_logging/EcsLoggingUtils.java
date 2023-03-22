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
package co.elastic.apm.agent.ecs_logging;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.ServiceInfo;
import co.elastic.apm.agent.impl.ElasticApmTracer;

import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Objects;

public class EcsLoggingUtils {

    private static final Logger log = LoggerFactory.getLogger(EcsLoggingUtils.class);

    @Nullable
    public static String getServiceName(ElasticApmTracer tracer) {
        ServiceInfo serviceInfo = tracer.getServiceInfoForClassLoader(Thread.currentThread().getContextClassLoader());
        String configuredServiceName = tracer.getConfig(CoreConfiguration.class).getServiceName();
        return serviceInfo != null ? serviceInfo.getServiceName() : configuredServiceName;
    }

    @Nullable
    public static String getServiceVersion(ElasticApmTracer tracer) {
        ServiceInfo serviceInfo = tracer.getServiceInfoForClassLoader(Thread.currentThread().getContextClassLoader());
        String configuredServiceVersion = tracer.getConfig(CoreConfiguration.class).getServiceVersion();
        return serviceInfo != null ? serviceInfo.getServiceVersion() : configuredServiceVersion;
    }

    public static void warnIfServiceNameMisconfigured(@Nullable String configuredValue, ElasticApmTracer tracer) {
        warnIfMisConfigured("service.name", configuredValue, getServiceName(tracer));
    }

    public static void warnIfServiceVersionMisconfigured(@Nullable String configuredValue, ElasticApmTracer tracer) {
        warnIfMisConfigured("service.version", configuredValue, getServiceVersion(tracer));
    }

    private static void warnIfMisConfigured(String key, @Nullable String configuredValue, @Nullable String agentValue) {
        if (!Objects.equals(agentValue, configuredValue)) {
            log.warn("configuration values differ for '{}': ecs-logging='{}', agent='{}', traces and logs might not correlate properly", key, configuredValue, agentValue);
        }
    }
}
