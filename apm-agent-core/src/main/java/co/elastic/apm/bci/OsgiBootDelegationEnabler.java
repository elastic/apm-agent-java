/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.bci;

import co.elastic.apm.context.LifecycleListener;
import co.elastic.apm.impl.ElasticApmTracer;

import java.util.Arrays;
import java.util.List;

/**
 * Required in OSGi environments like Equinox, which is used in WebSphere.
 * By adding the base package of the APM agent,
 * the instrumented classes have access to the agent classes,
 * without specifying {@code Import-Package} bundle headers.
 * <p>
 * Note: in Apache Felix the boot delegation only works for classes loaded by the bootstrap classloader,
 * which means that the whole agent code needs to be added to the bootstrap classloader.
 * See {@link AgentMain#init(java.lang.instrument.Instrumentation)}
 * </p>
 */
public class OsgiBootDelegationEnabler implements LifecycleListener {
    private static final List<String> bootdelegationNames = Arrays.asList("org.osgi.framework.bootdelegation", "atlassian.org.osgi.framework.bootdelegation");
    private static final String APM_BASE_PACKAGE = "co.elastic.apm.*";

    @Override
    public void start(ElasticApmTracer tracer) {
        for (String bootdelegationName : bootdelegationNames) {
            final String systemPackages = System.getProperty(bootdelegationName);
            if (systemPackages != null) {
                System.setProperty(bootdelegationName, systemPackages + "," + APM_BASE_PACKAGE);
            } else {
                System.setProperty(bootdelegationName, APM_BASE_PACKAGE);
            }
        }
    }

    @Override
    public void stop() {
        // noop
    }
}
